package com.aiplacementos.app.network

import com.aiplacementos.app.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

@Serializable
private data class RefreshRequestBody(@SerialName("refresh_token") val refreshToken: String)

@Serializable
private data class TokenResponseBody(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
)

/**
 * Transparently refreshes an expired access token on a 401 and retries the request once.
 * Without this, an expired access token (30 min server-side) surfaced as a generic "check
 * your connection" error on every screen even though a valid refresh token was already
 * sitting unused in TokenStore -- see network/TokenStore.kt.
 *
 * Uses its own bare OkHttpClient for the refresh call itself specifically so it does not
 * carry this same Authenticator, which would recurse.
 */
class TokenAuthenticator(private val tokenStore: TokenStore) : Authenticator {
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshClient = OkHttpClient.Builder().build()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null // already retried once -- give up
        if (isAuthEndpoint(response.request.url.encodedPath)) return null

        return runBlocking {
            val refreshToken = tokenStore.currentRefreshToken() ?: return@runBlocking null
            val newTokens = tryRefresh(refreshToken) ?: return@runBlocking null
            tokenStore.save(newTokens.accessToken, newTokens.refreshToken)
            response.request.newBuilder()
                .header("Authorization", "Bearer ${newTokens.accessToken}")
                .build()
        }
    }

    private fun isAuthEndpoint(path: String): Boolean =
        path.endsWith("/auth/login") || path.endsWith("/auth/signup") || path.endsWith("/auth/refresh")

    private fun tryRefresh(refreshToken: String): TokenResponseBody? {
        val body = json.encodeToString(RefreshRequestBody(refreshToken))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}auth/refresh")
            .post(body)
            .build()
        return try {
            refreshClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val responseBody = resp.body?.string() ?: return null
                json.decodeFromString<TokenResponseBody>(responseBody)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
