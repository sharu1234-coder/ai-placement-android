package com.aiplacementos.app.ui.voiceagent

import com.aiplacementos.app.voice.realtime.ConversationState
import com.aiplacementos.app.voice.realtime.InterruptionReason
import com.aiplacementos.app.voice.realtime.PendingOperation
import com.aiplacementos.app.voice.realtime.VoiceAgentMode

/**
 * What the Voice Agent screen renders, as types that cannot describe something that did not
 * happen.
 *
 * **The conversation phase is not re-modelled here.** It is `ConversationState` from
 * `RealtimeVoiceSession`, carried through unchanged. A second phase enum in the UI layer would
 * be a second state machine that could disagree with the one driving the socket -- and the
 * instruction is explicit that no second session machine gets built. What this file adds is the
 * part the conversation layer has no opinion about: the running transcript, the agent's replies,
 * and the notices a student has to be told about.
 *
 * Every field that could be a claim is nullable and stays null until the server actually said
 * so. There is no arrangement of these types that shows a transcript before `final_transcript`
 * arrives, an answer before `assistant_message` does, or a spoken reply when `spoken` was false.
 */
data class VoiceAgentUiState(
    val mode: VoiceAgentMode,
    /** The live phase, straight from the conversation layer. */
    val conversation: ConversationState = ConversationState.Idle,
    val micGranted: Boolean = false,
    /** Oldest first. Each entry is one committed turn. */
    val turns: List<VoiceAgentTurn> = emptyList(),
    /** Something the student must be told that is not a turn. At most one at a time. */
    val notice: VoiceAgentNotice? = null,
    val sessionId: String? = null,
    /**
     * Operations whose outcome this client cannot state -- a confirmed action that was in flight
     * when the connection died. Surfaced rather than retried: re-running a mutation whose result
     * is unknown is the one thing a reconnect must never do.
     */
    val unresolvedOperations: List<PendingOperation> = emptyList(),
) {
    /** True while the session is live enough to accept speech. */
    val isLive: Boolean
        get() = conversation !is ConversationState.Idle &&
            conversation !is ConversationState.Closed &&
            conversation !is ConversationState.Failed

    /** True when the agent is producing or playing a reply, so barge-in is meaningful. */
    val canInterrupt: Boolean
        get() = conversation is ConversationState.Generating ||
            conversation is ConversationState.Speaking ||
            conversation is ConversationState.Thinking ||
            conversation is ConversationState.ToolCalling

    val canStart: Boolean get() = micGranted && !isLive

    /** What the student is being asked to wait for, or null when nothing is pending. */
    val busyLabel: String?
        get() = when (conversation) {
            is ConversationState.Connecting -> "Connecting…"
            is ConversationState.Reconnecting -> "Reconnecting…"
            is ConversationState.Transcribing -> "Hearing you out…"
            is ConversationState.Thinking -> "Thinking…"
            is ConversationState.ToolCalling -> "Checking your records…"
            is ConversationState.Generating -> "Preparing a reply…"
            else -> null
        }
}

/**
 * One committed turn: what the student said, and what came back.
 *
 * Built empty when the turn is committed and filled in as the server reports each stage, so a
 * turn in progress is distinguishable from one that produced nothing. [failure] and [answer] are
 * not mutually exclusive on purpose -- a reply whose synthesis failed has both, and that is the
 * honest description of it.
 */
data class VoiceAgentTurn(
    val turnId: String,
    /** The student's words, from `final_transcript`. Null until recognition actually ran. */
    val transcript: String? = null,
    /** The agent's words, from `assistant_message`. Null until the responder returned one. */
    val answer: String? = null,
    /** Whether audio accompanied [answer]. False means read it; no voice is coming. */
    val spoken: Boolean = false,
    /** A typed stage failure from `turn_failed`. */
    val failure: VoiceAgentTurnFailure? = null,
) {
    val isComplete: Boolean get() = answer != null || failure != null
}

/**
 * A typed per-turn failure. **Not a session failure** -- the conversation continues.
 *
 * [code] is the server's closed vocabulary (`nothing_recognised`, `stt_unavailable`,
 * `turn_in_flight`, `tts_failed`, …) and is kept for diagnostics and tests; [message] is already
 * safe to show and is what the student reads. The code is never interpolated into the message
 * here, because the server's own mapping is the single place that decides what a student is told.
 */
data class VoiceAgentTurnFailure(val code: String, val message: String)

/** Something to tell the student that is not about one turn. */
sealed interface VoiceAgentNotice {
    val message: String

    /** The server timed the session out. Starting again is the only way forward. */
    data object SessionExpired : VoiceAgentNotice {
        override val message = "That voice session timed out. Start a new one when you are ready."
    }

    data class Interrupted(val reason: InterruptionReason) : VoiceAgentNotice {
        override val message = when (reason) {
            InterruptionReason.USER_SPEECH -> "Stopped. Go ahead."
            InterruptionReason.USER_STOPPED -> "Stopped."
            InterruptionReason.AUDIO_FOCUS_LOST -> "Another app took over the audio."
            InterruptionReason.ROUTE_CHANGED -> "Your headset changed, so playback stopped."
        }
    }

    /**
     * An action was in flight when the connection died, and its result is genuinely unknown.
     * Said plainly rather than guessed at, and never retried on the student's behalf.
     */
    data class OperationUnknown(val description: String) : VoiceAgentNotice {
        override val message =
            "\"$description\" may or may not have been saved. Check before trying again."
    }

    /** The session itself failed. [recoverable] decides whether a retry button is offered. */
    data class Failed(override val message: String, val recoverable: Boolean) : VoiceAgentNotice
}
