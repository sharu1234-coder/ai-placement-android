package com.aiplacementos.app.voice.realtime

import com.aiplacementos.app.voice.core.VoiceAudioError

/**
 * The conversation state machine (VA-2).
 *
 * VA-1 deliberately modelled only *audio* states, because that is what it owned. This is the
 * layer the locked spec's machine belongs to:
 *
 * ```
 * IDLE -> LISTENING <-> INTERRUPTED -> TRANSCRIBING -> THINKING
 *      -> TOOL_CALLING (zero or more) -> GENERATING -> SPEAKING -> LISTENING
 * ```
 *
 * [ConversationState.Thinking] and [ConversationState.ToolCalling] are present because the
 * service reports them and the UI must render them. VA-2 never *decides* to think or to call a
 * tool -- that is VA-4 and later. Modelling the state is not implementing the orchestrator.
 */
sealed interface ConversationState {
    data object Idle : ConversationState
    data object Connecting : ConversationState
    data object Listening : ConversationState
    data object Transcribing : ConversationState
    data object Thinking : ConversationState
    data object ToolCalling : ConversationState
    data object Generating : ConversationState
    data object Speaking : ConversationState

    /** Work is suspended but the session intends to carry on. */
    data class Interrupted(val reason: InterruptionReason) : ConversationState

    /** The connection dropped and a retry is in flight. Audio resources are still held. */
    data object Reconnecting : ConversationState

    data class Failed(val error: ConversationError) : ConversationState
    data object Closed : ConversationState
}

enum class InterruptionReason {
    /** The user spoke over the agent. */
    USER_SPEECH,

    /** Another app took audio focus. Surfaced up from the Voice Core. */
    AUDIO_FOCUS_LOST,

    /** Headset unplugged, Bluetooth dropped. */
    ROUTE_CHANGED,

    /** The user pressed stop. */
    USER_STOPPED,
}

/**
 * The spec's error states, typed.
 *
 * Separate from [VoiceAudioError] and [RealtimeError] rather than reusing either, because a
 * conversation can fail for reasons neither of those knows about -- an expired session, a tool
 * that failed server-side -- and because the UI's question is "can the user retry?", which
 * [isRecoverable] answers once instead of at every call site.
 */
sealed interface ConversationError {
    val message: String
    val isRecoverable: Boolean

    /** Maps the spec's PERMISSION_ERROR. */
    data object Permission : ConversationError {
        override val message = "Microphone permission is required to talk."
        override val isRecoverable = true
    }

    /** Maps AUDIO_ERROR. Wraps the Core's own typed failure rather than flattening it. */
    data class Audio(val cause: VoiceAudioError) : ConversationError {
        override val message = cause.message
        override val isRecoverable = cause.isRecoverable
    }

    /** Maps NETWORK_ERROR. */
    data class Network(val cause: RealtimeError) : ConversationError {
        override val message = cause.message
        override val isRecoverable = cause.isRetryable
    }

    /** Maps TOOL_ERROR. */
    data class Tool(val operationId: String, val detail: String) : ConversationError {
        override val message = "That action did not complete: $detail"
        override val isRecoverable = true
    }

    /** Maps AI_ERROR. */
    data class Ai(val detail: String) : ConversationError {
        override val message = "The assistant could not answer: $detail"
        override val isRecoverable = true
    }

    /** Maps SESSION_EXPIRED. Recoverable, but only by authenticating again. */
    data object SessionExpired : ConversationError {
        override val message = "This voice session expired. Start a new one."
        override val isRecoverable = true
    }
}

/**
 * Events reported out of [RealtimeVoiceSession] to whoever is rendering it.
 *
 * Audio is absent on purpose, exactly as in VA-1: frames go to playback, not onto a shared
 * stream where every observer would pin a buffer.
 */
sealed interface RealtimeEvent {
    /**
     * @param resumed whether this connection continued an earlier session. False on a first
     *   connect, and false after a reconnect whose grant the server did not honour.
     */
    data class Connected(val sessionId: String, val resumed: Boolean = false) : RealtimeEvent
    data object Reconnected : RealtimeEvent
    data class PartialTranscript(val turnId: String, val text: String) : RealtimeEvent
    data class FinalTranscript(val turnId: String, val text: String) : RealtimeEvent

    /** The assistant's reply as text. [spoken] is false when no audio accompanies it. */
    data class AssistantMessage(val turnId: String, val text: String, val spoken: Boolean) : RealtimeEvent

    /**
     * One turn did not produce an answer, or could not be spoken. Not a connection failure --
     * deliberately separate from [Failed], so a mumble cannot be rendered as an outage.
     */
    data class TurnFailed(val turnId: String, val code: String, val message: String) : RealtimeEvent
    data class UserTurnEnded(val speechMillis: Long) : RealtimeEvent
    data class BackchannelIgnored(val speechMillis: Long) : RealtimeEvent
    data class BargeIn(val generationId: String?) : RealtimeEvent
    data class Interrupted(val reason: InterruptionReason) : RealtimeEvent
    data class OperationResolved(val operationId: String, val succeeded: Boolean) : RealtimeEvent

    /**
     * An operation whose outcome cannot be determined -- the connection died while it was in
     * flight. Emitted so the UI can say so and offer reconciliation, never retried silently.
     */
    data class OperationUnknown(val operationId: String, val description: String) : RealtimeEvent

    data class Failed(val error: ConversationError) : RealtimeEvent
    data object Closed : RealtimeEvent
}

/**
 * Legal transitions, in one table.
 *
 * Same reasoning as VA-1's `VoiceStateMachine`: one place to answer "can this happen from
 * here?", testable without a socket or a microphone.
 */
object ConversationStateMachine {

    enum class Trigger {
        CONNECT,
        CONNECTED,
        USER_SPEECH_START,
        USER_TURN_END,
        TRANSCRIBING,
        THINKING,
        TOOL_CALL,
        GENERATION_START,
        GENERATION_END,
        BARGE_IN,
        EXTERNAL_INTERRUPT,
        RESUME,
        CONNECTION_LOST,
        RECONNECTED,
        FAIL,
        STOP,
    }

    /** A session that holds audio resources and can still exchange messages. */
    fun isLive(state: ConversationState): Boolean = when (state) {
        is ConversationState.Idle,
        is ConversationState.Closed,
        is ConversationState.Failed,
        -> false

        else -> true
    }

    /** True when native audio resources are held and cleanup must release them. */
    fun holdsResources(state: ConversationState): Boolean = when (state) {
        is ConversationState.Idle, is ConversationState.Closed -> false
        else -> true
    }

    fun isTerminal(state: ConversationState): Boolean =
        state is ConversationState.Closed || state is ConversationState.Failed

    fun isAllowed(state: ConversationState, trigger: Trigger): Boolean = when (trigger) {
        // Starting is only meaningful from nothing, for the same reason VA-1 refuses a second
        // open: it would orphan the first session's socket and recorder.
        Trigger.CONNECT -> state is ConversationState.Idle || state is ConversationState.Closed

        Trigger.CONNECTED -> state is ConversationState.Connecting ||
            state is ConversationState.Reconnecting

        // The user may start speaking at any moment of a live session -- that is what makes it
        // a conversation rather than a walkie-talkie.
        Trigger.USER_SPEECH_START -> isLive(state) && state !is ConversationState.Connecting

        Trigger.USER_TURN_END -> isLive(state) && state !is ConversationState.Connecting

        Trigger.TRANSCRIBING, Trigger.THINKING, Trigger.TOOL_CALL, Trigger.GENERATION_START ->
            isLive(state) && state !is ConversationState.Connecting

        Trigger.GENERATION_END -> state is ConversationState.Speaking ||
            state is ConversationState.Generating ||
            state is ConversationState.Interrupted

        // There has to be something to interrupt. Raising barge-in while merely listening
        // would cancel a generation that does not exist and confuse the service.
        Trigger.BARGE_IN -> state is ConversationState.Generating ||
            state is ConversationState.Speaking

        Trigger.EXTERNAL_INTERRUPT -> isLive(state)

        Trigger.RESUME -> state is ConversationState.Interrupted ||
            state is ConversationState.Reconnecting

        Trigger.CONNECTION_LOST -> isLive(state)

        Trigger.RECONNECTED -> state is ConversationState.Reconnecting

        Trigger.FAIL -> !isTerminal(state)

        // Stopping must be legal everywhere, including from Closed. VA-1's lesson: cleanup
        // that throws because it already ran is cleanup nobody dares call from a finally block.
        Trigger.STOP -> true
    }
}
