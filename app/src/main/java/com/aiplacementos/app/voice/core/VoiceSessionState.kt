package com.aiplacementos.app.voice.core

/**
 * Typed state and events for the shared Voice Core (VA-1).
 *
 * The state machine is deliberately about *audio*, not about conversation. The locked spec's
 * fuller machine (LISTENING -> TRANSCRIBING -> THINKING -> TOOL_CALLING -> GENERATING ->
 * SPEAKING) belongs to the realtime session in VA-2; modelling it here would mean inventing
 * states this phase cannot drive and cannot test. What VA-1 owns is the part that manages
 * native resources, and those are the states below.
 *
 * Nothing in this file imports an Android UI class. A ViewModel observes these; the audio
 * engine must not know a ViewModel exists.
 */
sealed interface VoiceSessionState {
    /** No session. No native resource is held. The only state in which cleanup is a no-op. */
    data object Idle : VoiceSessionState

    /** A session was requested and resources are being acquired (focus, recorder, player). */
    data object Preparing : VoiceSessionState

    /** Resources held, nothing flowing. The resting state of an open session. */
    data class Ready(val effects: VoiceAudioEffectsStatus) : VoiceSessionState

    /** Microphone capture is running. */
    data class Capturing(val effects: VoiceAudioEffectsStatus) : VoiceSessionState

    /** Audio is being played back. */
    data object Playing : VoiceSessionState

    /**
     * Capturing and playing at the same time.
     *
     * Added in VA-2. VA-1 treated capture and playback as mutually exclusive, which turned out
     * to be wrong for the product rather than merely conservative: barge-in requires the
     * microphone to stay open *while* the agent speaks, because the only way to detect that the
     * user interrupted is to hear them do it. A half-duplex session can offer a stop button; it
     * cannot offer an interruption.
     */
    data class Duplex(val effects: VoiceAudioEffectsStatus) : VoiceSessionState

    /**
     * Audio work was suspended by something outside the app -- transient focus loss, a call.
     * Distinct from [Ready] because the session intends to resume, and distinct from
     * [Failed] because nothing went wrong.
     */
    data class Interrupted(val reason: VoiceInterruption) : VoiceSessionState

    /** A terminal failure for this session. The session must be closed and reopened. */
    data class Failed(val error: VoiceAudioError) : VoiceSessionState

    /** Cleanup has run. Terminal, and reaching it more than once is safe. */
    data object Closed : VoiceSessionState
}

/** Why audio work was suspended without failing. */
enum class VoiceInterruption {
    /** Transient focus loss -- another app wants audio briefly. */
    AUDIO_FOCUS_LOST_TRANSIENT,

    /** Permanent focus loss -- another app took audio for the foreseeable future. */
    AUDIO_FOCUS_LOST,

    /** The output or input device changed under us (headset unplugged, Bluetooth gone). */
    AUDIO_ROUTE_CHANGED,
}

/**
 * Typed audio failures.
 *
 * Typed rather than a message string because callers act differently on them: permission
 * denial needs a permission prompt, focus denial needs a retry later, and an unsupported
 * configuration is a programming or device-compatibility problem that retrying cannot fix.
 * [isRecoverable] encodes that distinction once, instead of at each call site.
 */
sealed interface VoiceAudioError {
    val message: String

    /** True when retrying the same operation could plausibly succeed. */
    val isRecoverable: Boolean

    /** RECORD_AUDIO is not granted, or was revoked mid-session. */
    data object PermissionDenied : VoiceAudioError {
        override val message = "Microphone permission is required to use voice."
        override val isRecoverable = true
    }

    /** The audio configuration is structurally invalid or this device rejects it. */
    data class UnsupportedConfiguration(val detail: String) : VoiceAudioError {
        override val message = "This device cannot record in the required audio format: $detail"
        override val isRecoverable = false
    }

    /** The platform refused audio focus -- something else owns audio right now. */
    data object AudioFocusDenied : VoiceAudioError {
        override val message = "Another app is using audio right now. Try again in a moment."
        override val isRecoverable = true
    }

    /** AudioRecord/AudioTrack could not be constructed or did not reach INITIALIZED. */
    data class DeviceUnavailable(val detail: String) : VoiceAudioError {
        override val message = "The microphone or speaker is unavailable: $detail"
        override val isRecoverable = true
    }

    /** A read from the microphone failed mid-capture. */
    data class CaptureFailed(val detail: String) : VoiceAudioError {
        override val message = "Recording stopped unexpectedly: $detail"
        override val isRecoverable = true
    }

    /** A write to the output failed mid-playback. */
    data class PlaybackFailed(val detail: String) : VoiceAudioError {
        override val message = "Playback stopped unexpectedly: $detail"
        override val isRecoverable = true
    }

    /** An operation was attempted in a state that does not allow it. A caller bug. */
    data class IllegalTransition(val from: String, val operation: String) : VoiceAudioError {
        override val message = "Cannot $operation while $from."
        override val isRecoverable = false
    }
}

/**
 * Discrete things that happened, as opposed to the state that results.
 *
 * Both exist because they answer different questions: state answers "what is true now" for
 * rendering, events answer "what just happened" for reacting once. A UI that only had state
 * could not tell a second focus loss from the first.
 *
 * Raw PCM frames are deliberately NOT an event. Frames go to the capture sink that asked for
 * them; putting them on a shared event stream would publish audio to every observer and keep
 * buffers alive for as long as the slowest one.
 */
sealed interface VoiceEvent {
    data object SessionOpened : VoiceEvent
    data object SessionClosed : VoiceEvent
    data object CaptureStarted : VoiceEvent

    /** [frameCount] is a count, never the audio itself. */
    data class CaptureStopped(val durationMillis: Long, val frameCount: Long) : VoiceEvent
    data object PlaybackStarted : VoiceEvent
    data class PlaybackStopped(val cancelled: Boolean) : VoiceEvent
    data class FocusChanged(val hasFocus: Boolean, val transient: Boolean) : VoiceEvent
    data class RouteChanged(val route: VoiceAudioRoute) : VoiceEvent
    data class Interrupted(val reason: VoiceInterruption) : VoiceEvent
    data class Failed(val error: VoiceAudioError) : VoiceEvent
    data class EffectsResolved(val status: VoiceAudioEffectsStatus) : VoiceEvent

    /**
     * The session could not put the voice on the output it asked for.
     *
     * Distinct from [Failed] because it is not fatal: the conversation still works, it is just
     * coming out of the wrong place -- usually the earpiece, because another app owns the
     * communication device. Reported rather than swallowed so the screen can say so, since a
     * student whose assistant has gone quiet otherwise has no way to tell this apart from a
     * broken microphone.
     */
    data class OutputRoutingFailed(val error: VoiceAudioError) : VoiceEvent
}

/** Where audio is currently going or coming from, as far as the platform will tell us. */
enum class VoiceAudioRoute { EARPIECE, SPEAKER, WIRED_HEADSET, BLUETOOTH, UNKNOWN }

/**
 * The legal transitions, in one place.
 *
 * This is a table rather than scattered `if` checks so that "can a closed session start
 * capturing?" has exactly one answer, and so the rules can be tested without constructing any
 * audio hardware. [VoiceSession] consults it before acting; a rejected transition becomes
 * [VoiceAudioError.IllegalTransition] instead of a native crash or a silently ignored call.
 */
object VoiceStateMachine {

    /** The operations a caller can request. */
    enum class Operation { OPEN, START_CAPTURE, STOP_CAPTURE, START_PLAYBACK, STOP_PLAYBACK, CLOSE }

    fun isAllowed(state: VoiceSessionState, operation: Operation): Boolean = when (operation) {
        // Opening is only meaningful from nothing. Re-opening an open session is a caller bug,
        // not an idempotent convenience: it would orphan the first session's resources.
        Operation.OPEN -> state is VoiceSessionState.Idle || state is VoiceSessionState.Closed

        // Capture needs a session that holds its resources and is not already capturing.
        // Allowed from Playing because a realtime turn keeps the microphone open while the
        // agent speaks. Still NOT allowed from Interrupted: resuming has to go through the
        // focus path, otherwise the app records over whatever interrupted it.
        Operation.START_CAPTURE -> state is VoiceSessionState.Ready ||
            state is VoiceSessionState.Playing

        // Stopping is permitted from the resting state too, so a caller that lost track of
        // whether capture started does not have to guess. Idempotent by design.
        Operation.STOP_CAPTURE -> state is VoiceSessionState.Capturing ||
            state is VoiceSessionState.Duplex ||
            state is VoiceSessionState.Ready ||
            state is VoiceSessionState.Interrupted

        // Symmetrically, playback may begin while capture is running. That pair is
        // [VoiceSessionState.Duplex], and it is what makes barge-in detectable at all.
        Operation.START_PLAYBACK -> state is VoiceSessionState.Ready ||
            state is VoiceSessionState.Capturing

        Operation.STOP_PLAYBACK -> state is VoiceSessionState.Playing ||
            state is VoiceSessionState.Duplex ||
            state is VoiceSessionState.Ready ||
            state is VoiceSessionState.Interrupted

        // Closing must be legal from everywhere, including Closed. Cleanup that can throw
        // because it already ran is cleanup nobody dares call from a finally block.
        Operation.CLOSE -> true
    }

    /** True when [state] holds native audio resources that cleanup has to release. */
    fun holdsResources(state: VoiceSessionState): Boolean = when (state) {
        is VoiceSessionState.Idle, is VoiceSessionState.Closed -> false
        else -> true
    }

    /** True when no further work should be attempted on this session. */
    fun isTerminal(state: VoiceSessionState): Boolean =
        state is VoiceSessionState.Closed || state is VoiceSessionState.Failed
}
