package com.aiplacementos.app.network

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets

private val Context.dataStore by preferencesDataStore(name = "auth_tokens")

private const val ACCESS_TOKEN_AD = "access_token"
private const val REFRESH_TOKEN_AD = "refresh_token"

/**
 * Access/refresh JWTs are encrypted at rest with a Tink AEAD (AES256-GCM) key that is itself
 * wrapped by an Android Keystore master key (`android-keystore://`) -- the wrapped keyset blob
 * in "token_keyset_prefs" is useless without that hardware-backed key, which never leaves
 * secure storage and does not survive backup/restore to another device. DataStore is still the
 * persistence layer (unchanged file/keys, so the existing backup-exclusion rules still apply);
 * only the stored values are now ciphertext instead of plaintext.
 *
 * androidx.security:security-crypto (EncryptedSharedPreferences) was deprecated by Google in
 * April 2025 -- this is the currently-recommended replacement pattern (DataStore + Tink +
 * Keystore), not a home-grown scheme.
 */
class TokenStore(private val context: Context) {
    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")

    init {
        AeadConfig.register()
    }

    private val aead: Aead by lazy {
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "token_keyset", "token_keyset_prefs")
            .withKeyTemplate(com.google.crypto.tink.KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://ai_placement_os_token_master_key")
            .build()
            .keysetHandle
            .getPrimitive(com.google.crypto.tink.RegistryConfiguration.get(), Aead::class.java)
    }

    private fun encrypt(plaintext: String, associatedData: String): String {
        val ciphertext = aead.encrypt(plaintext.toByteArray(StandardCharsets.UTF_8), associatedData.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    /** Null on any decryption failure (corrupted/tampered/foreign-keyset data) rather than
     * throwing -- callers treat a null token as "not logged in," which is the safe fallback
     * (forces a fresh login) instead of crashing the app. */
    private fun decrypt(ciphertextB64: String, associatedData: String): String? = try {
        val plaintext = aead.decrypt(Base64.decode(ciphertextB64, Base64.NO_WRAP), associatedData.toByteArray(StandardCharsets.UTF_8))
        String(plaintext, StandardCharsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[accessTokenKey]?.let { decrypt(it, ACCESS_TOKEN_AD) }
    }

    suspend fun currentAccessToken(): String? = accessToken.first()

    suspend fun currentRefreshToken(): String? =
        context.dataStore.data.map { prefs -> prefs[refreshTokenKey]?.let { decrypt(it, REFRESH_TOKEN_AD) } }.first()

    suspend fun save(accessToken: String, refreshToken: String) {
        context.dataStore.edit {
            it[accessTokenKey] = encrypt(accessToken, ACCESS_TOKEN_AD)
            it[refreshTokenKey] = encrypt(refreshToken, REFRESH_TOKEN_AD)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
