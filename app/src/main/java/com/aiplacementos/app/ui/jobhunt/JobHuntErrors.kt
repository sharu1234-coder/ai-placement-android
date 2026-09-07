package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
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

    /** The student has not chosen a role or resume yet, so there is nothing to search on. */
    data class SetupRequired(override val message: String) : JobSearchFailure {
        override val isRetryable: Boolean get() = false
    }
}

fun Throwable.toJobSearchFailure(): JobSearchFailure = when {
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
