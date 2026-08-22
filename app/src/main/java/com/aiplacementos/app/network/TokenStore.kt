package com.aiplacementos.app.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "auth_tokens")

/**
 * Unencrypted DataStore for now (see UI_DESIGN_REFERENCES.md decision log). Move to
 * EncryptedSharedPreferences/DataStore+crypto before any real user testing.
 */
class TokenStore(private val context: Context) {
    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")

    val accessToken: Flow<String?> = context.dataStore.data.map { it[accessTokenKey] }

    suspend fun currentAccessToken(): String? = accessToken.first()

    suspend fun currentRefreshToken(): String? =
        context.dataStore.data.map { it[refreshTokenKey] }.first()

    suspend fun save(accessToken: String, refreshToken: String) {
        context.dataStore.edit {
            it[accessTokenKey] = accessToken
            it[refreshTokenKey] = refreshToken
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
