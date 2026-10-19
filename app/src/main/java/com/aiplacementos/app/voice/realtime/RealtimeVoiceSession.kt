package com.aiplacementos.app.voice.realtime

import com.aiplacementos.app.voice.core.PcmFrameSink
import com.aiplacementos.app.voice.core.VoiceAudioConfig
import com.aiplacementos.app.voice.core.VoiceEvent
import com.aiplacementos.app.voice.core.VoiceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** How far an operation has got, from this client's point of view. */
enum class OperationStatus { IN_FLIGHT, SUCCEEDED, FAILED, UNKNOWN }

/**
 * A side-effecting operation the service is carrying out on the user's behalf.
 *
 * [description] exists so that an unknown outcome can be *described* to the user. "Something
 * may or may not have happened" is not a usable message; "your note may or may not have been
 * saved" is.
 */
data class PendingOperation(
    val id: String,
    val description: String,
    val status: OperationStatus,
)

data class ReconnectPolicy(
    val maxAttempts: Int = 4,
    val initialDelayMillis: Long = 400,
    val maxDelayMillis: Long = 8_000,
    val backoffFactor: Double = 2.0,
) {
    /** @param attempt 1-based. */
    fun delayFor(attempt: Int): Long {
        if (attempt <= 1) return initialDelayMillis
        var delay = initialDelayMillis.toDouble()
        repeat(attempt - 1) { delay *= backoffFactor }
        return delay.toLong().coerceAtMost(maxDelayMillis)
    }
}

/**
 * The realtime conversation (VA-2).
 *
 * Sits between VA-1's [VoiceSession], which owns the microphone and speaker, and a
 * [RealtimeTransport], which owns the connection. It owns neither: it owns the *conversation* --
 * whose turn it is, what to do when the user talks over the agent, and what is true about an
 * operation whose answer never arrived.
 *
 * ## What it deliberately does not do
 *
 * No intent classification, no tool selection, no model choice, no personal-data retrieval.
 * Those are VA-4 and later. This class routes bytes and state; it does not decide anything
 * about what the agent should say.
 *
 * ## Threading
 *
 * Three threads meet here and the split is deliberate:
 *
 * - **The capture thread** calls [onCapturedFrame]. Everything on that path is non-blocking:
 *   VAD arithmetic, a `trySend`, a `tryEmit`. Blocking it backs up into `AudioRecord` and the
 *   microphone starts dropping frames.
 * - **The transport's callback thread** calls [onServerEvent]. Same rule.
 * - **[scope]** runs everything that needs to suspend, including every call back into
 *   [VoiceSession], whose own mutex must never be taken from a callback thread.
 *
 * [mutex] guards only lifecycle (acquiring and releasing resources). State transitions use
 * `compareAndSet` on the [StateFlow] so that an event arriving mid-lifecycle cannot deadlock.
 */
class RealtimeVoiceSession(
    private val audio: VoiceSession,
    private val transport: RealtimeTransport,
    private val config: VoiceAudioConfig,
    private val scope: CoroutineScope,
    private val vad: VoiceActivityDetector = EnergyVoiceActivityDetector(),
    private val turnDetector: TurnDetector = TurnDetector(),
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
    private val locale: String = "en-IN",
    /**
     * Which authoritative path answers every turn on this session. Fixed for the session's
     * lifetime, including across a reconnect: a resumed session that silently changed mode would
     * send an interview answer to the coach, or a coaching question to the grader.
     */
    private val mode: VoiceAgentMode = VoiceAgentMode.COACH,
    /** Required by [VoiceAgentMode.INTERVIEW]; the server refuses the handshake without it. */
    private val interview: InterviewContext? = null,
) {

    private val mutex = Mutex()

    private val _state = MutableStateFlow<ConversationState>(ConversationState.Idle)
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RealtimeEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<RealtimeEvent> = _events.asSharedFlow()

    /**
     * Identity of the audio session: bumped on [start] and [stop], never on a reconnect.
     *
     * The same device VA-1 uses, for the same reason -- a callback from a previous session is
     * entitled to run, and nothing but identity distinguishes it from a current one.
     */
    private val sessionGeneration = AtomicInteger(0)

    /**
     * Identity of the *connection*: bumped on every connect, including each reconnect attempt.
     *
     * Kept separate from [sessionGeneration] because the two have genuinely different
     * lifetimes. Sharing one counter meant a reconnect silently invalidated the microphone
     * sink -- capture was never restarted, so every frame was then rejected as stale and the
     * session went deaf the moment the network recovered.
     */
    private val connectionGeneration = AtomicInteger(0)

    private val operations = ConcurrentHashMap<String, PendingOperation>()

    /** Chunks dropped because the playback queue was full. Backpressure, counted not hidden. */
    private val droppedChunks = AtomicLong(0)

    /** Audio frames the transport refused. Counted so a silent failure becomes a visible one. */
    private val droppedFrames = AtomicLong(0)

    @Volatile
    private var sessionId: String? = null

    @Volatile
    private var resumeToken: String? = null

    /**
     * The generation currently being played, or null when nothing is.
     *
     * Also the filter that discards audio from a generation the user already interrupted:
     * barge-in clears it, so chunks still in flight are dropped instead of resuming playback
     * of a sentence the user cut off.
     */
    @Volatile
    private var currentGenerationId: String? = null

    @Volatile
    private var agentSpeaking = false

    private var playbackQueue: Channel<PlaybackCommand>? = null
    private var playbackJob: Job? = null
    private var audioEventJob: Job? = null
    private var reconnectJob: Job? = null

    val droppedChunkCount: Long get() = droppedChunks.get()
    val droppedFrameCount: Long get() = droppedFrames.get()
    val currentSessionId: String? get() = sessionId

    // ----------------------------------------------------------------- lifecycle

    /**
     * Acquire audio, connect, and begin listening.
     *
     * Ordering matters and is the reverse of teardown: the microphone is opened first, because
     * a session the user has not granted permission for should fail before a socket is opened
     * and a server-side session is allocated.
     */
    suspend fun start(): ConversationError? = mutex.withLock {
        if (!ConversationStateMachine.isAllowed(_state.value, ConversationStateMachine.Trigger.CONNECT)) {
            return@withLock ConversationError.Ai("A voice session is already running.")
        }
        _state.value = ConversationState.Connecting
        val mySession = sessionGeneration.incrementAndGet()
        val myConnection = connectionGeneration.incrementAndGet()

        audio.open()?.let { error ->
            return@withLock failAndCleanup(ConversationError.Audio(error))
        }

        val connectError = transport.connect(sessionConfig(), listenerFor(myConnection))
        if (connectError != null) {
            return@withLock failAndCleanup(ConversationError.Network(connectError))
        }

        startPlaybackPump()
        observeAudioEvents(mySession)

        vad.reset()
        turnDetector.reset()
        audio.startCapture(sinkFor(mySession))?.let { error ->
            return@withLock failAndCleanup(ConversationError.Audio(error))
        }

        transition(ConversationStateMachine.Trigger.CONNECTED, ConversationState.Listening)
        null
    }

    /**
     * Stop everything and release resources. Idempotent, and safe from any state.
     *
     * Teardown order is the reverse of [start]: stop producing before stopping the consumer, so
     * nothing is written to a closed sink. Every in-flight operation becomes
     * [OperationStatus.UNKNOWN] before the connection goes, because once it is gone there is no
     * way left to learn what happened.
     */
    suspend fun stop() = mutex.withLock {
        sessionGeneration.incrementAndGet() // orphan every callback still in flight
        connectionGeneration.incrementAndGet()

        markInFlightOperationsUnknown()

        audio.stopCapture()
        audio.stopPlayback(cancelled = true)
        agentSpeaking = false
        currentGenerationId = null

        reconnectJob?.cancel()
        reconnectJob = null
        playbackQueue?.close()
        playbackQueue = null
        playbackJob?.cancel()
        playbackJob = null
        audioEventJob?.cancel()
        audioEventJob = null

        transport.disconnect()
        audio.close()

        _state.value = ConversationState.Closed
        _events.tryEmit(RealtimeEvent.Closed)
    }

    /** The user pressed stop, or something outside the app took over. */
    suspend fun interrupt(reason: InterruptionReason) {
        if (!transition(
                ConversationStateMachine.Trigger.EXTERNAL_INTERRUPT,
                ConversationState.Interrupted(reason),
            )
        ) {
            return
        }
        audio.stopPlayback(cancelled = true)
        agentSpeaking = false
        transport.send(RealtimeClientEvent.Interrupt(currentGenerationId))
        currentGenerationId = null
        _events.tryEmit(RealtimeEvent.Interrupted(reason))
    }

    // ----------------------------------------------------------------- operations

    /**
     * Start tracking a side-effecting operation.
     *
     * Tracking is separate from executing on purpose. VA-2 does not execute anything; it
     * records that something is outstanding so that a dropped connection can be reported as
     * "unknown" rather than quietly assumed to have failed -- or, worse, retried.
     */
    fun trackOperation(id: String, description: String) {
        operations[id] = PendingOperation(id, description, OperationStatus.IN_FLIGHT)
    }

    fun operation(id: String): PendingOperation? = operations[id]

    /** Everything whose outcome this client cannot currently state. */
    fun unresolvedOperations(): List<PendingOperation> =
        operations.values.filter {
            it.status == OperationStatus.IN_FLIGHT || it.status == OperationStatus.UNKNOWN
        }

    /**
     * Ask the service to abandon an operation.
     *
     * The operation stays IN_FLIGHT, not CANCELLED: a cancel that races a completing mutation
     * does not undo it, and the only thing that may resolve an operation is an authoritative
     * [RealtimeServerEvent.OperationOutcome].
     */
    fun cancelOperation(id: String) {
        transport.send(RealtimeClientEvent.CancelOperation(id))
    }

    private fun markInFlightOperationsUnknown() {
        for (pending in operations.values) {
            if (pending.status != OperationStatus.IN_FLIGHT) continue
            operations[pending.id] = pending.copy(status = OperationStatus.UNKNOWN)
            _events.tryEmit(RealtimeEvent.OperationUnknown(pending.id, pending.description))
        }
    }

    // ----------------------------------------------------------------- capture path

    private fun sinkFor(mySession: Int) = PcmFrameSink { buffer, length ->
        if (mySession != sessionGeneration.get()) return@PcmFrameSink
        onCapturedFrame(buffer, length)
    }

    /**
     * One microphone frame. Runs on the capture thread; must not block.
     *
     * Audio is forwarded whether or not the local VAD called it speech. Trimming to detected
     * speech would save bandwidth but clips word onsets -- the detector needs a few frames to
     * be sure, and those frames are the start of the word. On-device VAD drives *turn-taking*;
     * it does not gate the stream.
     */
    private fun onCapturedFrame(buffer: ByteArray, length: Int) {
        val frame = vad.process(buffer, length)
        if (transport.sendAudio(buffer, length) != null) droppedFrames.incrementAndGet()

        when (val turn = turnDetector.onFrame(frame, config.millisForBytes(length), agentSpeaking)) {
            // While the agent is speaking, the user merely starting to talk changes nothing:
            // it may be a backchannel. Only [bargeIn] decides it was an interruption, and
            // until then the conversation is still in Speaking.
            TurnEvent.SpeechStarted -> if (!agentSpeaking) {
                transition(ConversationStateMachine.Trigger.USER_SPEECH_START, ConversationState.Listening)
            }

            is TurnEvent.SpeechEnded -> {
                _events.tryEmit(RealtimeEvent.UserTurnEnded(turn.speechMillis))
                // Committing a turn that happened *over* the agent would ask the service to
                // respond to an interjection it was never meant to hear.
                if (!agentSpeaking) {
                    transport.send(RealtimeClientEvent.CommitTurn)
                    transition(ConversationStateMachine.Trigger.USER_TURN_END, ConversationState.Transcribing)
                }
            }

            is TurnEvent.BackchannelIgnored ->
                _events.tryEmit(RealtimeEvent.BackchannelIgnored(turn.speechMillis))

            // Suspending work cannot happen here, so hand it to the scope. The audible part of
            // barge-in is the first thing that coroutine does.
            TurnEvent.BargeIn -> scope.launch { bargeIn() }

            null -> Unit
        }
    }

    /**
     * The user spoke over the agent and meant it.
     *
     * Local playback stops *before* the service is told. The user judges an interruption by
     * when the voice stops, and a round trip is tens to hundreds of milliseconds they would
     * otherwise spend talking over it.
     */
    private suspend fun bargeIn() {
        if (!transition(
                ConversationStateMachine.Trigger.BARGE_IN,
                ConversationState.Interrupted(InterruptionReason.USER_SPEECH),
            )
        ) {
            return
        }
        val interrupted = currentGenerationId
        audio.stopPlayback(cancelled = true)
        agentSpeaking = false
        currentGenerationId = null // discards chunks of this generation still in the queue
        transport.send(RealtimeClientEvent.Interrupt(interrupted))
        _events.tryEmit(RealtimeEvent.BargeIn(interrupted))
        transition(ConversationStateMachine.Trigger.RESUME, ConversationState.Listening)
    }

    // ----------------------------------------------------------------- playback path

    private sealed interface PlaybackCommand {
        class Chunk(val event: RealtimeServerEvent.AudioChunk) : PlaybackCommand
        data class Done(val generationId: String, val cancelled: Boolean) : PlaybackCommand
    }

    /**
     * A single consumer drains the playback queue.
     *
     * Audio chunks cannot be `launch`ed individually: coroutines started concurrently finish in
     * whatever order the dispatcher chooses, and speech reassembled out of order is noise. One
     * queue with one consumer makes ordering a property of the design rather than of timing.
     */
    private fun startPlaybackPump() {
        val queue = Channel<PlaybackCommand>(capacity = PLAYBACK_QUEUE_CAPACITY)
        playbackQueue = queue
        playbackJob = scope.launch {
            for (command in queue) {
                when (command) {
                    is PlaybackCommand.Chunk -> playChunk(command.event)
                    is PlaybackCommand.Done -> finishGeneration(command.generationId, command.cancelled)
                }
            }
        }
    }

    private suspend fun playChunk(chunk: RealtimeServerEvent.AudioChunk) {
        // Dropped rather than played: this generation was interrupted or superseded.
        if (chunk.generationId != currentGenerationId) return
        if (!agentSpeaking) {
            val error = audio.startPlayback()
            if (error != null) {
                failWith(ConversationError.Audio(error))
                return
            }
            agentSpeaking = true
            transition(ConversationStateMachine.Trigger.GENERATION_START, ConversationState.Speaking)
        }
        audio.writePlayback(chunk.pcm, chunk.length)
    }

    private suspend fun finishGeneration(generationId: String, cancelled: Boolean) {
        if (generationId != currentGenerationId) return
        audio.stopPlayback(cancelled = cancelled)
        agentSpeaking = false
        currentGenerationId = null
        transition(ConversationStateMachine.Trigger.GENERATION_END, ConversationState.Listening)
    }

    /**
     * Play a WAV through the Voice Core -- the VA-1 compatibility gap, closed.
     *
     * This is what lets a file-based asset (an interview question rendered server-side) go
     * through [VoiceSession] instead of a second `MediaPlayer`. The interview *upload* contract
     * is untouched; only playback becomes expressible through the shared Core.
     */
    suspend fun playWav(wav: ByteArray): ConversationError? {
        val decoded = when (val result = WavPcm.toPlaybackFormat(wav, config)) {
            is WavDecodeResult.Failure -> return ConversationError.Ai("Audio could not be read: ${result.reason}")
            is WavDecodeResult.Success -> result
        }
        audio.startPlayback()?.let { return ConversationError.Audio(it) }
        agentSpeaking = true
        transition(ConversationStateMachine.Trigger.GENERATION_START, ConversationState.Speaking)

        val chunk = config.bytesForMillis(PLAYBACK_CHUNK_MILLIS).coerceAtLeast(config.bytesPerFrame)
        var offset = 0
        while (offset < decoded.pcm.size) {
            // Re-checked every chunk so that a barge-in or a stop takes effect mid-file rather
            // than after the whole utterance has been written.
            if (!agentSpeaking) break
            val length = minOf(chunk, decoded.pcm.size - offset)
            val slice = decoded.pcm.copyOfRange(offset, offset + length)
            audio.writePlayback(slice, length)?.let { return ConversationError.Audio(it) }
            offset += length
        }

        if (agentSpeaking) {
            audio.stopPlayback(cancelled = false)
            agentSpeaking = false
            transition(ConversationStateMachine.Trigger.GENERATION_END, ConversationState.Listening)
        }
        return null
    }

    // ----------------------------------------------------------------- server events

    private fun listenerFor(myConnection: Int) = RealtimeTransport.Listener { event ->
        onServerEvent(myConnection, event)
    }

    /** Runs on the transport's thread. Non-blocking; anything suspending goes to [scope]. */
    private fun onServerEvent(myConnection: Int, event: RealtimeServerEvent) {
        // A late event from a connection we have already replaced. It is entitled to arrive;
        // it is not entitled to be acted on.
        if (myConnection != connectionGeneration.get()) return

        when (event) {
            is RealtimeServerEvent.SessionAccepted -> {
                sessionId = event.sessionId
                resumeToken = event.resumeToken
                _events.tryEmit(RealtimeEvent.Connected(event.sessionId))
            }

            is RealtimeServerEvent.PartialTranscript -> {
                transition(ConversationStateMachine.Trigger.TRANSCRIBING, ConversationState.Transcribing)
                _events.tryEmit(RealtimeEvent.PartialTranscript(event.turnId, event.text))
            }

            is RealtimeServerEvent.FinalTranscript ->
                _events.tryEmit(RealtimeEvent.FinalTranscript(event.turnId, event.text))

            is RealtimeServerEvent.AssistantMessage ->
                _events.tryEmit(RealtimeEvent.AssistantMessage(event.turnId, event.text, event.spoken))

            // Not routed through [handleConnectionLoss]: the session is fine. Surfaced so the UI
            // can say what went wrong with this one turn and let the student try again.
            is RealtimeServerEvent.TurnFailed ->
                _events.tryEmit(RealtimeEvent.TurnFailed(event.turnId, event.code, event.message))

            // The service's own VAD is informational. On-device detection stays authoritative
            // for barge-in because it does not pay a network round trip to notice.
            is RealtimeServerEvent.SpeechStarted, is RealtimeServerEvent.SpeechStopped -> Unit

            RealtimeServerEvent.Thinking ->
                transition(ConversationStateMachine.Trigger.THINKING, ConversationState.Thinking)

            is RealtimeServerEvent.ToolCallStarted -> {
                trackOperation(event.operationId, event.name)
                transition(ConversationStateMachine.Trigger.TOOL_CALL, ConversationState.ToolCalling)
            }

            is RealtimeServerEvent.GenerationStarted -> {
                currentGenerationId = event.generationId
                transition(ConversationStateMachine.Trigger.GENERATION_START, ConversationState.Generating)
            }

            is RealtimeServerEvent.AudioChunk -> enqueue(PlaybackCommand.Chunk(event))

            is RealtimeServerEvent.GenerationDone ->
                enqueue(PlaybackCommand.Done(event.generationId, cancelled = false))

            is RealtimeServerEvent.GenerationCancelled ->
                enqueue(PlaybackCommand.Done(event.generationId, cancelled = true))

            is RealtimeServerEvent.OperationOutcome -> resolveOperation(event)

            RealtimeServerEvent.SessionExpired ->
                scope.launch { failWith(ConversationError.SessionExpired) }

            is RealtimeServerEvent.Failed -> scope.launch { handleConnectionLoss(event.error) }

            is RealtimeServerEvent.Closed -> scope.launch {
                handleConnectionLoss(RealtimeError.ServerClosed(event.code, event.reason))
            }
        }
    }

    private fun enqueue(command: PlaybackCommand) {
        val queue = playbackQueue ?: return
        if (queue.trySend(command).isFailure) droppedChunks.incrementAndGet()
    }

    private fun resolveOperation(event: RealtimeServerEvent.OperationOutcome) {
        val pending = operations[event.operationId]
        val status = if (event.succeeded) OperationStatus.SUCCEEDED else OperationStatus.FAILED
        operations[event.operationId] = pending?.copy(status = status)
            ?: PendingOperation(event.operationId, event.detail.orEmpty(), status)
        _events.tryEmit(RealtimeEvent.OperationResolved(event.operationId, event.succeeded))
        if (!event.succeeded) {
            _events.tryEmit(
                RealtimeEvent.Failed(
                    ConversationError.Tool(event.operationId, event.detail ?: "no detail given"),
                ),
            )
        }
    }

    // ----------------------------------------------------------------- audio events

    private fun observeAudioEvents(mySession: Int) {
        audioEventJob = scope.launch {
            launch {
                audio.events.collect { event ->
                    if (mySession != sessionGeneration.get()) return@collect
                    when (event) {
                        is VoiceEvent.Interrupted -> {
                            agentSpeaking = false
                            val reason = InterruptionReason.AUDIO_FOCUS_LOST
                            if (transition(
                                    ConversationStateMachine.Trigger.EXTERNAL_INTERRUPT,
                                    ConversationState.Interrupted(reason),
                                )
                            ) {
                                _events.tryEmit(RealtimeEvent.Interrupted(reason))
                            }
                        }

                        is VoiceEvent.Failed -> failWith(ConversationError.Audio(event.error))

                        else -> Unit
                    }
                }
            }

            // Resumption keys off the Core's own state rather than its focus *event*. VA-1
            // emits FocusChanged before it moves the session back to Ready, so a listener that
            // reacted to the event would try to restart capture from Interrupted and be
            // refused. The state flow cannot be observed too early.
            launch {
                audio.state.collect { audioState ->
                    if (mySession != sessionGeneration.get()) return@collect
                    if (audioState is com.aiplacementos.app.voice.core.VoiceSessionState.Ready &&
                        _state.value is ConversationState.Interrupted
                    ) {
                        resumeAfterInterruption()
                    }
                }
            }
        }
    }

    /**
     * Focus came back.
     *
     * The Core returns itself to Ready but deliberately does not reopen the microphone -- VA-1
     * decided that regaining focus must never silently restart recording. Restarting is this
     * layer's call to make, and only from an interruption it is still in.
     */
    private suspend fun resumeAfterInterruption() {
        if (_state.value !is ConversationState.Interrupted) return
        vad.reset()
        turnDetector.reset()
        val error = audio.startCapture(sinkFor(sessionGeneration.get()))
        if (error != null) {
            failWith(ConversationError.Audio(error))
            return
        }
        transition(ConversationStateMachine.Trigger.RESUME, ConversationState.Listening)
    }

    // ----------------------------------------------------------------- connection loss

    /**
     * The connection went away.
     *
     * Every in-flight operation becomes UNKNOWN **before** any reconnect is attempted. That
     * ordering is the whole point: an operation that was executing when the socket died has an
     * outcome this client does not know, and a reconnect does not retroactively reveal it. The
     * spec's rule is that unknown states are reconciled, never blindly retried, so nothing here
     * re-sends anything.
     */
    private suspend fun handleConnectionLoss(error: RealtimeError) {
        if (ConversationStateMachine.isTerminal(_state.value)) return
        markInFlightOperationsUnknown()
        agentSpeaking = false
        currentGenerationId = null

        if (!error.isRetryable) {
            failWith(ConversationError.Network(error))
            return
        }
        if (!transition(ConversationStateMachine.Trigger.CONNECTION_LOST, ConversationState.Reconnecting)) {
            return
        }
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch { reconnectLoop(error) }
    }

    private suspend fun reconnectLoop(cause: RealtimeError) {
        var lastError = cause
        for (attempt in 1..reconnectPolicy.maxAttempts) {
            delay(reconnectPolicy.delayFor(attempt))
            if (_state.value !is ConversationState.Reconnecting) return

            val myConnection = connectionGeneration.incrementAndGet()
            val error = transport.connect(sessionConfig(), listenerFor(myConnection))
            if (error == null) {
                transition(ConversationStateMachine.Trigger.RECONNECTED, ConversationState.Listening)
                resumeToken?.let { transport.send(RealtimeClientEvent.Resume(it)) }
                _events.tryEmit(RealtimeEvent.Reconnected)
                return
            }
            lastError = error
            if (!error.isRetryable) break
        }
        failWith(ConversationError.Network(lastError))
    }

    private fun sessionConfig() = RealtimeSessionConfig(
        sampleRateHz = config.sampleRateHz,
        channelCount = config.channelCount,
        resumeToken = resumeToken,
        locale = locale,
        mode = mode,
        interview = interview,
    )

    // ----------------------------------------------------------------- helpers

    /**
     * Apply a transition if the table allows it.
     *
     * `compareAndSet` rather than a plain assignment: events arrive from two callback threads
     * and the capture thread, and a lost update here would leave the UI showing a state the
     * session is no longer in.
     */
    private fun transition(
        trigger: ConversationStateMachine.Trigger,
        next: ConversationState,
    ): Boolean {
        while (true) {
            val current = _state.value
            if (!ConversationStateMachine.isAllowed(current, trigger)) return false
            if (current == next) return false
            if (_state.compareAndSet(current, next)) return true
        }
    }

    private fun failWith(error: ConversationError) {
        if (transition(ConversationStateMachine.Trigger.FAIL, ConversationState.Failed(error))) {
            _events.tryEmit(RealtimeEvent.Failed(error))
        }
    }

    /** Release whatever [start] had acquired before it failed, then report. */
    private suspend fun failAndCleanup(error: ConversationError): ConversationError {
        playbackQueue?.close()
        playbackQueue = null
        playbackJob?.cancel()
        playbackJob = null
        audioEventJob?.cancel()
        audioEventJob = null
        transport.disconnect()
        audio.close()
        _state.value = ConversationState.Failed(error)
        _events.tryEmit(RealtimeEvent.Failed(error))
        return error
    }

    private companion object {
        /**
         * Roughly four seconds of 20 ms chunks. Large enough to ride out a scheduling hiccup,
         * small enough that a stalled consumer surfaces as counted drops rather than unbounded
         * memory growth.
         */
        const val PLAYBACK_QUEUE_CAPACITY = 200
        const val PLAYBACK_CHUNK_MILLIS = 20L
    }
}
