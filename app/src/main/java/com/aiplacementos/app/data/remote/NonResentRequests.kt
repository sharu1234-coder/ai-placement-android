package com.aiplacementos.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit

/**
 * [client] for every call, except that OkHttp never re-sends a Career Path generation by itself.
 *
 * When a request fails after it was written, on a connection that came from OkHttp's pool,
 * OkHttp sends it again on a new connection without telling anyone. Harmless for a read. For
 * POST /career-path/generate it is a second generation reaching the server while the first is
 * still running -- F12-03's overlap, started by the transport rather than by a tap. Measured, not
 * assumed: with its connection dropped after the request arrived, MockWebServer received the POST
 * twice (CareerPathGenerationLifecycleTest). A lost generation response is handled by
 * CareerPathGenerationRecovery instead, which reads the server's state rather than asking again.
 *
 * Only this one request: every other call keeps OkHttp's default. The exception is a clone of
 * [client] -- same interceptors, authenticator, timeouts and connection pool -- so a 401 is still
 * refreshed and followed up; retryOnConnectionFailure only governs connection-failure retries.
 */
internal fun Retrofit.Builder.clientNotResendingGenerations(client: OkHttpClient): Retrofit.Builder {
    val neverResends = client.newBuilder().retryOnConnectionFailure(false).build()
    return callFactory { request ->
        if (request.isCareerPathGeneration()) neverResends.newCall(request) else client.newCall(request)
    }
}

private fun Request.isCareerPathGeneration(): Boolean =
    method == "POST" && url.encodedPath.endsWith("/career-path/generate")
