package com.aiplacementos.app.network

import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches a Firebase App Check token to every outgoing request, matching what the backend's
 * app/core/app_check.py dependency expects on the X-Firebase-AppCheck header. The provider
 * itself (Play Integrity in release, Debug in dev builds) is installed once in
 * AiPlacementApp.onCreate(); this interceptor only fetches and attaches its token per request.
 *
 * Fails OPEN, not closed: if fetching a token fails or times out (Play Integrity attestation
 * can be transiently unavailable -- no network, a fresh install before Play Integrity has
 * warmed up, an emulator with no Play Store, ...), the request proceeds without the header
 * rather than blocking it entirely. This matches the backend's own default posture --
 * FIREBASE_APP_CHECK_ENABLED defaults to false there, so an occasional missing header changes
 * nothing until a deployment explicitly turns enforcement on; even then, degrading a specific
 * request to "unverified app instance" is preferable to bricking the whole app over a
 * transient attestation hiccup.
 *
 * OkHttp interceptors are synchronous, so this blocks the calling (already-background,
 * OkHttp-dispatched) thread on Tasks.await() rather than restructuring every repository call
 * into a suspend-aware token fetch -- the same tradeoff AuthInterceptor already makes with
 * runBlocking for the stored access token.
 */
class AppCheckInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestWithToken = try {
            val token = Tasks.await(Firebase.appCheck.getAppCheckToken(false), 10, TimeUnit.SECONDS)
            request.newBuilder().header("X-Firebase-AppCheck", token.token).build()
        } catch (e: Exception) {
            request
        }
        return chain.proceed(requestWithToken)
    }
}
