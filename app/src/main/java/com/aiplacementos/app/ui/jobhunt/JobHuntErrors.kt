package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.data.remote.JobHuntLimitEnvelopeDto
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Why Job Hunt needs its own classifier on top of the shared [toUserMessage].
 *
 * A job search has failure modes no other screen in this app has, and telling a student the
 * wrong one is a real product harm. "We could not reach the job service" and "there are no jobs
 * matching you" are opposite claims -- one is about us, the other is a statement about the
 * labour market -- and a single generic error string would blur them. The backend deliberately
 * answers with distinct status codes for exactly this reason, so the client never has to parse
 * message text to tell them apart:
 *
 *   503 -- no provider configured yet, or its credentials were rejected. Job Hunt genuinely is
 *          not available; retrying will not help, so no retry is offered.
 *   429 -- the shared provider quota is exhausted. Retrying later genuinely will help.
 *   504 -- the provider was too slow. Immediate retry is reasonable.
 *   502 -- the provider is down or returned something unusable.
 *   403 -- an advanced filter was used without Premium. Not a failure of the search at all.
 *   402 -- a free student's one Job Hunt search is already used. Also not a failure: the
 *          search was refused, not attempted, and buying Premium is what changes it. It is a
 *          separate code from 403 on purpose -- both are "you need Premium" but they lead to
 *          different screens, and telling them apart by reading the message text would make a
 *          copy edit a bug.
 *   409 -- the request id named a different operation. Only reachable from a client bug or a
 *          replayed key; the fix is a fresh search, not a retry of this one.
 */
sealed interface JobSearchFailure {
    val message: String
    val isRetryable: Boolean

    /** The feature is not set up. Deliberately distinct from "no results" and from an outage. */
    data class Unavailable(override val message: String) : JobSearchFailure {
        override val isRetryable: Boolean get() = false
    }

    /** A transient problem -- provider down, timed out, quota exhausted, or no connectivity. */
    data class Transient(override val message: String, val isNetworkError: Boolean = false) :
        JobSearchFailure {
        override val isRetryable: Boolean get() = true
    }

    /** A Premium-only filter was used. The search itself is fine without it. */
    data class PremiumRequired(override val message: String) : JobSearchFailure {
        override val isRetryable: Boolean get() = false
    }

    /**
     * The free Job Hunt search has been used, and this student has no subscription.
     *
     * Not retryable, and deliberately not phrased as an error: nothing went wrong and nothing
     * is broken. The screen renders this as the upgrade prompt with a "Maybe later" beside it,
     * never as a failure state with a Retry button -- a retry would be refused identically, and
     * offering one would make a deliberate product boundary look like a bug.
     */
    data class FreeLimitReached(override val message: String) : JobSearchFailure {
        override val isRetryable: Boolean get() = false
    }

    /** The student has not chosen a role or resume yet, so there is nothing to search on. */
    data class SetupRequired(override val message: String) : JobSearchFailure {
        override val isRetryable: Boolean get() = false
    }
}

/**
 * Lenient by construction: this parses an *error* body, and an error handler that throws is
 * worse than the error it was handling. Unknown keys are ignored so a server that adds a field
 * does not break the client, and every field on the DTO has a default so a shape that drifted
 * still yields a usable message.
 */
private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * The server's own wording for a 402, or null.
 *
 * Preferred over a hardcoded string because the server owns this copy -- it is the side that
 * knows the plan, the wording and the language. The fallback exists because an error body can
 * be missing, truncated or unparseable, and "you have used your free search" is still true
 * whatever the body said. `code` is checked rather than trusted blindly, so a 402 from
 * somewhere unexpected does not get rendered as if it were this one.
 */
private fun HttpException.freeLimitMessage(): String? = runCatching {
    val body = response()?.errorBody()?.string().orEmpty()
    val detail = errorJson.decodeFromString<JobHuntLimitEnvelopeDto>(body).detail
    detail.message.takeIf { it.isNotBlank() && detail.code == "JOB_HUNT_FREE_LIMIT_REACHED" }
}.getOrNull()

fun Throwable.toJobSearchFailure(): JobSearchFailure = when {
    this is HttpException && code() == 402 -> JobSearchFailure.FreeLimitReached(
        freeLimitMessage() ?: "You've used your free Job Hunt search.",
    )
    this is HttpException && code() == 409 -> JobSearchFailure.Transient(
        "That search couldn't be repeated. Please start a new search.",
    )
    this is HttpException && code() == 503 -> JobSearchFailure.Unavailable(
        "Job Hunt isn't available yet. We're working on bringing real job listings to you.",
    )
    this is HttpException && code() == 429 -> JobSearchFailure.Transient(
        "Too many searches right now. Please try again in a few minutes.",
    )
    this is HttpException && code() == 504 -> JobSearchFailure.Transient(
        "The job search took too long. Please try again.",
    )
    this is HttpException && code() == 502 -> JobSearchFailure.Transient(
        "The job service isn't responding right now. Please try again shortly.",
    )
    this is HttpException && code() == 403 -> JobSearchFailure.PremiumRequired(
        "Advanced filters are a Premium feature. Search is free without them.",
    )
    this is HttpException && code() == 422 -> JobSearchFailure.SetupRequired(
        "Add a target role or upload your resume to start matching jobs.",
    )
    // Everything else -- including a genuine connectivity failure -- falls through to the
    // app-wide classifier so Job Hunt phrases those exactly like every other screen.
    else -> JobSearchFailure.Transient(
        toUserMessage("search for jobs"),
        isNetworkError = isNetworkError() && !isTimeoutError(),
    )
}
