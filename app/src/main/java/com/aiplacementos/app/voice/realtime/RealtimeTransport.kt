package com.aiplacementos.app.voice.realtime

/**
 * The provider-neutral realtime transport seam (VA-2).
 *
 * Nothing in this file names a vendor, a URL, a wire format or an HTTP client. That is the
 * whole point of it: the locked spec requires that business logic talk to interfaces while
 * provider SDKs, URLs and credential handling live behind adapters, so that a second
 * implementation -- OpenAI Realtime, Gemini Live, our own gateway -- can be added without
 * touching [RealtimeVoiceSession].
 *
 * Audio and control travel on separate methods deliberately. [RealtimeTransport.sendAudio]
 * takes raw PCM rather than an envelope because base64-ing every 20 ms frame into JSON inflates
 * the bytes on the wire by a third, for the one message type that is sent most often.
 */

/**
 * Which authoritative path answers a turn.
 *
 * A closed enum, and the server validates it against its own closed enum and refuses an unknown
 * value with a non-retryable close. Chosen once when the session opens, never per turn and never
 * from the content of what the student said -- a mode derived from a transcript would let the
 * student's own words decide whether their answer gets graded.
 */
enum class VoiceAgentMode(val wire: String) {
    /** The read-only career coach. Grounded answers from the student's own records. */
    COACH("coach"),

    /** The authoritative interview engine. Every turn stores a durable answer. */
    INTERVIEW("interview"),
}

/**
 * Which interview a session is answering, for [VoiceAgentMode.INTERVIEW].
 *
 * Both ids are checked against the authenticated owner **before the socket is accepted**, so an
 * interview belonging to someone else fails the handshake rather than one turn later. Only the
 * first question id is sent: the server advances its own pointer from the authoritative next
 * question, and the client never names a question id again.
 */
data class InterviewContext(
    val interviewId: Int,
    val firstQuestionId: Int,
)

/** What one realtime session needs to negotiate before audio flows. */
data class RealtimeSessionConfig(
    val sampleRateHz: Int,
    val channelCount: Int,
    val mode: VoiceAgentMode = VoiceAgentMode.COACH,
    /** Required by [VoiceAgentMode.INTERVIEW] and ignored otherwise. */
    val interview: InterviewContext? = null,
    /**
     * Non-null when resuming a session that dropped. The token comes from a previous
     * [RealtimeServerEvent.SessionAccepted]; the client never invents one.
     */
    val resumeToken: String? = null,
    /**
     * Default reflects the product's primary market rather than the device locale, because the
     * blueprint calls for benchmarking against Indian-English samples. Callers may override.
     */
    val locale: String = "en-IN",
)

enum class TransportState { Disconnected, Connecting, Connected, Reconnecting, Closed }

/**
 * Typed transport failures.
 *
 * [isRetryable] exists so that the reconnect policy is a property of the error rather than a
 * judgement each call site re-makes. Retrying a rejected credential loops forever; retrying a
 * dropped TCP connection is the correct response to the most common failure there is.
 */
sealed interface RealtimeError {
    val message: String
    val isRetryable: Boolean

    data class ConnectFailed(val detail: String) : RealtimeError {
        override val message = "Could not reach the voice service: $detail"
        override val isRetryable = true
    }

    /** Credentials were rejected. Reconnecting with the same token just loops. */
    data object AuthFailed : RealtimeError {
        override val message = "This voice session was not authorised."
        override val isRetryable = false
    }

    /** The peer sent something the codec cannot interpret. Reconnecting will not fix a bug. */
    data class ProtocolViolation(val detail: String) : RealtimeError {
        override val message = "The voice service sent an unexpected message: $detail"
        override val isRetryable = false
    }

    data object Timeout : RealtimeError {
        override val message = "The voice service did not respond in time."
        override val isRetryable = true
    }

    data object NetworkLost : RealtimeError {
        override val message = "The network connection was lost."
        override val isRetryable = true
    }

    data class ServerClosed(val code: Int, val reason: String) : RealtimeError {
        override val message = "The voice service closed the session ($code): $reason"
        override val isRetryable = code !in NON_RETRYABLE_CLOSE_CODES
    }

    /** A send was attempted with no live connection. A caller or ordering bug. */
    data object NotConnected : RealtimeError {
        override val message = "No realtime connection is open."
        override val isRetryable = false
    }

    companion object {
        /**
         * 1008 is the RFC 6455 policy-violation close; 4401/4403 are the application-level
         * equivalents of 401/403. Reconnecting after any of them repeats the same rejection.
         */
        val NON_RETRYABLE_CLOSE_CODES = setOf(1008, 4401, 4403)
    }
}

/** Control messages the client sends. Audio does not appear here; it has its own path. */
sealed interface RealtimeClientEvent {
    /** The user's turn is over; the service may finalise its transcript and respond. */
    data object CommitTurn : RealtimeClientEvent

    /**
     * Stop generating and speaking now.
     *
     * Sent on barge-in. [generationId] is the generation being interrupted, so that a service
     * which has already moved on does not cancel the wrong one; null means "whatever is
     * current", which is all a client can say when it has not yet seen a generation id.
     */
    data class Interrupt(val generationId: String?) : RealtimeClientEvent

    data class Resume(val token: String) : RealtimeClientEvent

    /**
     * The student deliberately finished the conversation.
     *
     * This is the *only* thing that lets the server record a conversation as `completed`.
     * Without it, a clean ending was indistinguishable on the wire from a dropped socket, and
     * the backend resolved that ambiguity the wrong way: every abnormal ending -- idle
     * timeout, lost signal, oversized frame, protocol violation -- was persisted as
     * `completed`, so a student who lost signal mid-interview got a record saying they
     * finished it.
     *
     * Sent only from an explicit end, never from backgrounding the app or from ViewModel
     * teardown. Leaving the screen is an interruption, not a completion.
     */
    data object EndSession : RealtimeClientEvent

    /**
     * Ask the service to abandon work for [operationId].
     *
     * Note this is a request, not a guarantee: a cancel that races a completing mutation leaves
     * the outcome unknown, which is why [RealtimeVoiceSession] tracks operations separately
     * rather than assuming a cancel succeeded.
     */
    data class CancelOperation(val operationId: String) : RealtimeClientEvent

    data object Heartbeat : RealtimeClientEvent
}

/** Everything the service can tell us. */
sealed interface RealtimeServerEvent {
    /**
     * @param resumed whether the server continued the session this client asked to resume.
     *   Reported by the server rather than inferred by the client: a grant that was presented
     *   is not evidence that it was honoured, and treating it as evidence is what allowed a
     *   reconnect to look successful while starting a new conversation.
     */
    data class SessionAccepted(
        val sessionId: String,
        val resumeToken: String?,
        val resumed: Boolean = false,
    ) : RealtimeServerEvent

    data class PartialTranscript(val turnId: String, val text: String) : RealtimeServerEvent
    data class FinalTranscript(val turnId: String, val text: String) : RealtimeServerEvent

    /**
     * The assistant's own words for a turn, as authoritative text.
     *
     * [FinalTranscript] is what the *student* said; this is the reply. Before it existed the
     * reply reached the device only as PCM in the audio frames, which meant a turn whose speech
     * synthesis failed had an answer the server could not deliver -- and `error` could not carry
     * it, because this client treats an `error` frame as a non-retryable protocol violation and
     * ends the session on it.
     *
     * [spoken] is false when no audio follows. Render the text; do not wait for a voice.
     */
    data class AssistantMessage(
        val turnId: String,
        val text: String,
        val spoken: Boolean,
    ) : RealtimeServerEvent

    /**
     * One turn produced no answer, or produced one it could not speak. **The session is still
     * healthy** -- this is not a connection failure and must not be handled as one.
     *
     * [code] is a closed server-side vocabulary (`no_audio`, `nothing_recognised`,
     * `stt_unavailable`, `turn_in_flight`, `tts_failed`, ...) and [message] is already safe to
     * show. A `TurnFailed` arriving after an [AssistantMessage] for the same turn means a stage
     * failed, not that the answer was lost.
     */
    data class TurnFailed(
        val turnId: String,
        val code: String,
        val message: String,
    ) : RealtimeServerEvent

    /** The service's own VAD fired. On-device VAD stays authoritative for barge-in latency. */
    data class SpeechStarted(val turnId: String) : RealtimeServerEvent
    data class SpeechStopped(val turnId: String) : RealtimeServerEvent

    data object Thinking : RealtimeServerEvent

    /**
     * The service began a tool call. VA-2 renders this state; it does not choose, validate or
     * execute tools -- that is VA-4 and later, and keeping it out is a scope boundary, not an
     * oversight.
     */
    data class ToolCallStarted(val operationId: String, val name: String) : RealtimeServerEvent

    data class GenerationStarted(val generationId: String) : RealtimeServerEvent

    /**
     * Not a data class: [pcm] is an array, and array equality is identity, so a generated
     * `equals` would quietly mislead anyone comparing two chunks.
     */
    class AudioChunk(
        val pcm: ByteArray,
        val length: Int,
        val generationId: String,
    ) : RealtimeServerEvent

    data class GenerationDone(val generationId: String) : RealtimeServerEvent
    data class GenerationCancelled(val generationId: String) : RealtimeServerEvent

    /**
     * The authoritative outcome of a tracked operation.
     *
     * This is the only thing that may resolve a pending operation. A tool call *starting* is
     * not evidence it finished, and the spec's anti-hallucination rule turns on exactly that
     * distinction.
     */
    data class OperationOutcome(
        val operationId: String,
        val succeeded: Boolean,
        val detail: String?,
    ) : RealtimeServerEvent

    data object SessionExpired : RealtimeServerEvent
    data class Failed(val error: RealtimeError) : RealtimeServerEvent
    data class Closed(val code: Int, val reason: String) : RealtimeServerEvent
}

/**
 * One realtime connection.
 *
 * [sendAudio] and [send] are deliberately **not** suspending. They are called from the audio
 * capture thread, which must never block waiting on a coroutine dispatcher: a stalled sink
 * backs up into the recorder and the microphone starts dropping frames. Implementations queue
 * or drop internally and report a [RealtimeError] instead of blocking.
 */
interface RealtimeTransport {
    val state: TransportState

    /** @return null on success, or why the connection could not be established. */
    suspend fun connect(config: RealtimeSessionConfig, listener: Listener): RealtimeError?

    fun sendAudio(buffer: ByteArray, length: Int): RealtimeError?

    fun send(event: RealtimeClientEvent): RealtimeError?

    /** Idempotent. Safe to call when never connected and safe to call twice. */
    suspend fun disconnect()

    fun interface Listener {
        fun onEvent(event: RealtimeServerEvent)
    }
}
