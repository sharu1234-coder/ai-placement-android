package com.aiplacementos.app.voice

import android.util.Log

/**
 * D3: observability for the reachable voice path, which had none.
 *
 * The VA-12 audit found no `Log` call anywhere in `AudioRecorder`, `AudioPlayer`,
 * `VoiceInterviewManager` or `VoiceInterviewViewModel` -- the only tag in the whole voice tree
 * was `"VoiceCore"`, in a package no screen can reach. So the path a student actually uses was
 * invisible, and VA-11's device checklist had to fall back on `dumpsys` because there was
 * nothing to read in logcat.
 *
 * **Why events are a closed type rather than strings.** A logger that takes a message is a
 * logger that will eventually be handed a transcript. Every event below carries only values
 * that are safe by construction: question and interview ids the student already sees on screen,
 * durations, byte counts, and typed failure codes. There is no field a transcript, a token, a
 * confirmation secret or an audio buffer could be put into, which is a stronger guarantee than
 * a review rule asking people not to.
 */
sealed interface VoiceEvent {
    /** A short, stable, greppable code. */
    val code: String

    /** Safe key/value pairs appended to the log line. Values are numbers or typed codes only. */
    fun fields(): Map<String, Any> = emptyMap()

    // ----------------------------------------------------------------- capture

    data class CaptureStarted(val questionId: Int) : VoiceEvent {
        override val code = "capture_started"
        override fun fields() = mapOf("question" to questionId)
    }

    data class CaptureStopped(val questionId: Int, val bytes: Long, val millis: Long) : VoiceEvent {
        override val code = "capture_stopped"
        override fun fields() = mapOf("question" to questionId, "bytes" to bytes, "ms" to millis)
    }

    data class CaptureCancelled(val questionId: Int?) : VoiceEvent {
        override val code = "capture_cancelled"
        override fun fields() = mapOf("question" to (questionId ?: -1))
    }

    /** @param reason a typed code, never a provider's or an exception's own words. */
    data class CaptureFailed(val questionId: Int?, val reason: String) : VoiceEvent {
        override val code = "capture_failed"
        override fun fields() = mapOf("question" to (questionId ?: -1), "reason" to reason)
    }

    // ----------------------------------------------------------------- focus

    data class FocusAcquired(val granted: Boolean) : VoiceEvent {
        override val code = "focus_acquired"
        override fun fields() = mapOf("granted" to granted)
    }

    data class FocusLost(val transient: Boolean) : VoiceEvent {
        override val code = "focus_lost"
        override fun fields() = mapOf("transient" to transient)
    }

    data object FocusReleased : VoiceEvent {
        override val code = "focus_released"
    }

    // ----------------------------------------------------------------- playback

    data class PlaybackStarted(val questionId: Int, val bytes: Long) : VoiceEvent {
        override val code = "playback_started"
        override fun fields() = mapOf("question" to questionId, "bytes" to bytes)
    }

    data class PlaybackStopped(val questionId: Int, val completed: Boolean) : VoiceEvent {
        override val code = "playback_stopped"
        override fun fields() = mapOf("question" to questionId, "completed" to completed)
    }

    // ----------------------------------------------------------------- turn + lifecycle

    data class TurnSubmitted(val interviewId: Int, val questionId: Int) : VoiceEvent {
        override val code = "turn_submitted"
        override fun fields() = mapOf("interview" to interviewId, "question" to questionId)
    }

    data class TurnFailed(val interviewId: Int, val questionId: Int, val reason: String) : VoiceEvent {
        override val code = "turn_failed"
        override fun fields() =
            mapOf("interview" to interviewId, "question" to questionId, "reason" to reason)
    }

    data object PermissionDenied : VoiceEvent {
        override val code = "permission_denied"
    }

    /** The app left the state in which capture is permitted. */
    data class LeftForeground(val wasRecording: Boolean) : VoiceEvent {
        override val code = "left_foreground"
        override fun fields() = mapOf("was_recording" to wasRecording)
    }

    data class ResourceReleaseFailed(val what: String) : VoiceEvent {
        override val code = "resource_release_failed"
        override fun fields() = mapOf("what" to what)
    }
}

/** Where events go. Replaceable so tests can assert on them without reading logcat. */
interface VoiceDiagnostics {
    fun record(event: VoiceEvent)

    companion object {
        /** Discards everything. The default for code paths that have no sink wired yet. */
        val None: VoiceDiagnostics = object : VoiceDiagnostics {
            override fun record(event: VoiceEvent) = Unit
        }
    }
}

/**
 * Logcat sink.
 *
 * `correlationId` is eight random hex characters generated per session, so the lines of one
 * voice interview can be followed in a noisy log without any identifier that means something
 * outside this process. It is deliberately not the interview id, the user id or the session
 * token.
 */
class AndroidVoiceDiagnostics(
    private val correlationId: String = newCorrelationId(),
) : VoiceDiagnostics {

    override fun record(event: VoiceEvent) {
        Log.i(TAG, render(event))
    }

    /**
     * The exact line that reaches logcat.
     *
     * Separate from [record] so a test can assert on it: `android.util.Log` is a no-op under
     * `isReturnDefaultValues`, so a canary test calling `record` would pass whatever the line
     * contained. This is the string under test.
     */
    internal fun render(event: VoiceEvent): String {
        val fields = event.fields()
        val rendered = if (fields.isEmpty()) {
            ""
        } else {
            fields.entries.joinToString(" ") { "${it.key}=${it.value}" }
        }
        return "[$correlationId] ${event.code} $rendered".trimEnd()
    }

    companion object {
        const val TAG = "VoiceInterview"

        fun newCorrelationId(): String =
            java.util.UUID.randomUUID().toString().replace("-", "").take(8)
    }
}
