package com.aiplacementos.app.network

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.IOException
import java.net.UnknownHostException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Single choke point for network-level error/performance observability -- covers every
 * Retrofit call (every repository shares one OkHttpClient via NetworkModule) without adding
 * reporting calls to each ViewModel/repository individually.
 *
 * Deliberately conservative about what gets escalated to Crashlytics as a non-fatal, to avoid
 * flooding the dashboard with expected/user-caused noise:
 *  - HTTP 5xx responses (real backend/server bugs) are recorded.
 *  - HTTP 4xx responses are only logged locally -- most (401 during token refresh, 404,
 *    422 validation) are expected app-level flows the calling ViewModel's own catch block
 *    already surfaces to the user.
 *  - UnknownHostException (device has no route to the server at all) is only logged locally --
 *    not an app bug, and the single most common "failure" a mobile client sees.
 *  - Every other IOException (timeouts, TLS failures, connection resets, etc.) is recorded.
 *
 * Never lets an observability failure break the actual network request: if Crashlytics itself
 * isn't reachable (e.g. Firebase not initialized, as in this class's own unit tests), the
 * reporting call is swallowed and the response/exception still flows through normally.
 */
class ObservabilityInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method = request.method
        val path = request.url.encodedPath
        val startMs = System.currentTimeMillis()

        try {
            val response = chain.proceed(request)
            val durationMs = System.currentTimeMillis() - startMs
            logResult(method, path, response.code, durationMs)

            if (response.code >= 500) {
                recordNonFatal(IOException("HTTP ${response.code} on $method $path"))
            }

            return response
        } catch (e: IOException) {
            val durationMs = System.currentTimeMillis() - startMs
            Log.w(TAG, "$method $path failed after ${durationMs}ms: ${e.javaClass.simpleName}")

            if (e !is UnknownHostException) {
                recordNonFatal(e)
            }
            throw e
        }
    }

    private fun logResult(method: String, path: String, code: Int, durationMs: Long) {
        val message = "$method $path -> $code (${durationMs}ms)"
        if (durationMs >= SLOW_REQUEST_THRESHOLD_MS) {
            Log.w(TAG, "Slow request: $message")
        } else {
            Log.d(TAG, message)
        }
    }

    private fun recordNonFatal(throwable: Throwable) {
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to report to Crashlytics: ${t.javaClass.simpleName}")
        }
    }

    companion object {
        private const val TAG = "ObservabilityInterceptor"
        // Generous on purpose: resume/JD parsing calls run real AI inference server-side and
        // can legitimately take well over a second (see NetworkModule's 150s read timeout for
        // that same reason). This only ever affects which log level a call is recorded at --
        // never delays or rejects anything.
        const val SLOW_REQUEST_THRESHOLD_MS = 3000L
    }
}
