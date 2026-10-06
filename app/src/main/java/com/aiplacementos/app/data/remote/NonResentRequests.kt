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
 *
 * ## Why this list is one endpoint and not twenty-four (F12-12)
 *
 * Phase 24 audited every POST in the app and found 24 whose backend handler inserts
 * unconditionally -- no natural key, no existence check, no idempotency key -- so OkHttp's
 * resend can leave a second row behind on any of them, not just this one. Broadening the list to
 * all 24 was tried, and reverted, because it makes the app worse:
 *
 *  - OkHttp's retry covers two different events. One is "the request was written and the
 *    response was lost", where a resend may duplicate. The other is "the pooled connection was
 *    already dead", where nothing reached the server and a resend is simply correct -- and that
 *    second one is the most common network event this app sees, because it happens on the first
 *    tap after the phone has been idle.
 *  - Turning the retry off cannot separate them. A successful TCP write does not mean the server
 *    received anything, so neither an Interceptor nor an EventListener can tell the cases apart;
 *    this is the Two Generals problem, not a missing OkHttp API.
 *  - Measured both ways: with the wider list in place, a live run's first tap on the Test Center
 *    failed with "Couldn't reach the server" and the server's request log showed the POST had
 *    never arrived. `NonIdempotentPostResendTest` reproduces that as a control.
 *
 * So the real rule is narrower than "non-idempotent": switch the retry off only where the app
 * can recover from an unknown outcome by reading server state instead of asking again.
 * /career-path/generate qualifies because [CareerPathGenerationRecovery] exists for exactly that.
 * Nothing else has a reconciliation path, so for those a lost response has to stay recoverable by
 * retrying it.
 *
 * Making the other 23 safe needs the duplicate collapsed where the truth lives -- an
 * Idempotency-Key the server dedupes on, as `/job-hunt/search` already does via JobHuntUsage's
 * unique (profile_id, idempotency_key). That is a backend change and is written up rather than
 * started here.
 */
internal fun Retrofit.Builder.clientNotResendingGenerations(client: OkHttpClient): Retrofit.Builder {
    val neverResends = client.newBuilder().retryOnConnectionFailure(false).build()
    return callFactory { request ->
        if (request.isCareerPathGeneration()) neverResends.newCall(request) else client.newCall(request)
    }
}

private fun Request.isCareerPathGeneration(): Boolean =
    method == "POST" && url.encodedPath.endsWith("/career-path/generate")
