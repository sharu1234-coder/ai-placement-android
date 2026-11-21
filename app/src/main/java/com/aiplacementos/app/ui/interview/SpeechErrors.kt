package com.aiplacementos.app.ui.interview

import com.aiplacementos.app.ui.common.classify
import com.aiplacementos.app.ui.common.isRetryable
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Why the interview screens need this on top of the shared classifier.
 *
 * The shared [classify] maps every 5xx to `ErrorKind.SERVER`, which is retryable -- correct for
 * an outage and exactly wrong for the one 503 this app can return forever. When the configured
 * provider cannot transcribe, the two speech-gated interview routes refuse every request
 * identically until the server is reconfigured, so "Something went wrong on our server. Please
 * try again." plus a Try Again button is a loop with no exit, landing on the feature the
 * product leads with.
 *
 * The server distinguishes the two for us rather than making the client guess from message
 * text: a capability refusal carries `code: "speech_not_supported"`, a real outage does not.
 * Same reasoning as the distinct status codes in [com.aiplacementos.app.ui.jobhunt] -- a copy
 * edit on the server must never become a client bug.
 *
 * Note this is a backstop, not the fix. The fix is asking GET /capabilities/speech *before*
 * offering a voice control, which is what [SpeechAvailability] is for. This path only runs when
 * a client asked anyway -- an interview opened from a deep link or a stale back stack, or a
 * server reconfigured mid-session.
 */
sealed interface InterviewVoiceFailure {
    val message: String
    val isRetryable: Boolean

    /**
     * This server cannot do speech, and no amount of retrying changes that.
     *
     * Deliberately not phrased as a fault: nothing broke and the student did nothing wrong.
     * The recovery is a different route, not another attempt, so the screen offers the typed
     * interview instead of a Retry.
     */
    data class SpeechUnsupported(override val message: String) : InterviewVoiceFailure {
        override val isRetryable: Boolean get() = false
    }

    /** Everything else, carrying the shared classifier's own retry verdict rather than a
     *  second opinion -- transient failures must keep the retry that can genuinely help. */
    data class Other(override val message: String, override val isRetryable: Boolean) : InterviewVoiceFailure
}

/**
 * Lenient by construction, for the same reason Job Hunt's parser is: this reads an *error*
 * body, and an error handler that throws is worse than the error it was handling. Unknown keys
 * are ignored and every field has a default, so a drifted shape still yields a usable answer.
 */
private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Serializable
private data class CapabilityRefusalDto(val code: String = "", val detail: String = "")

/** The refusal marker, or null for a 503 that is a genuine outage. */
private fun HttpException.speechRefusal(): CapabilityRefusalDto? = runCatching {
    val body = response()?.errorBody()?.string().orEmpty()
    errorJson.decodeFromString<CapabilityRefusalDto>(body).takeIf { it.code == "speech_not_supported" }
}.getOrNull()

fun Throwable.toInterviewVoiceFailure(action: String): InterviewVoiceFailure {
    val refusal = (this as? HttpException)?.takeIf { it.code() == 503 }?.speechRefusal()
    if (refusal != null) {
        // The server's own wording is preferred -- it is the side that knows what it cannot do
        // -- with a fallback because an error body can be missing, truncated or unparseable
        // while the refusal it described is still true.
        return InterviewVoiceFailure.SpeechUnsupported(
            refusal.detail.takeIf { it.isNotBlank() }
                ?: "Voice is not available on this server. You can type your answer instead.",
        )
    }
    val kind = classify()
    return InterviewVoiceFailure.Other(
        message = toUserMessage(action),
        isRetryable = kind.isRetryable,
    )
}
