package com.aiplacementos.app.voice.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The shared Voice Core session (VA-1).
 *
 * One object owns the microphone, the player, audio focus and route observation for the
 * duration of one user-visible voice session, and is the only thing permitted to change
 * [state]. That single-ownership rule is the point of the class: the readiness checklist
 * recorded "no audio focus handling" and "no protection against configuration changes or
 * process death mid-turn" as concrete gaps, and both existed because recorder and player were
 * held by a composition-scoped object with nothing coordinating them.
 *
 * What this is NOT, deliberately:
 *
 *  * Not a conversation state machine. Transcription, thinking, tool calls and generation are
 *    VA-2 and later; the states here are about native resources.
 *  * Not a network client. No transport, no provider, no STT/TTS.
 *  * Not a foreground service. Sessions start from a user-visible screen, per the spec's
 *    Android/Play constraints, and nothing here keeps the microphone open in the background.
 *  * Not aware of the UI. No Android lifecycle or Compose type is imported.
 *
 * Thread-safety: every public suspend function serialises on [mutex], so two callers cannot
 * interleave an open with a close. Callbacks from the platform (focus, routing) arrive on
 * arbitrary threads and are handled without taking the lock, which is why they are guarded by
 * the session generation rather than by mutual exclusion.
 */
class VoiceSession(
    private val config: VoiceAudioConfig = VoiceAudioConfig(),
    private val permission: MicrophonePermission,
    private val focus: AudioFocusController,
    private val capture: PcmCapture,
    private val playback: PcmPlayback,
    private val routes: AudioRouteMonitor,
    /**
     * Chooses which output the assistant's voice comes out of.
     *
     * Separate from [routes], which only observes. The split is deliberate and is the whole
     * fix for the "too quiet / wrong speaker" defect: observation alone left nothing in the
     * app able to ask for the loudspeaker, so communication audio went to the earpiece on the
     * voice-call stream. See [AudioOutputRouter].
     */
    private val output: AudioOutputRouter = AudioOutputRouter.None,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    private val _state = MutableStateFlow<VoiceSessionState>(VoiceSessionState.Idle)
    val state: StateFlow<VoiceSessionState> = _state.asStateFlow()

    /**
     * Events, replayless and non-suspending to emit.
     *
     * extraBufferCapacity with DROP_OLDEST rather than an unlimited buffer: an emit from a
     * platform callback must never suspend, and a slow collector must never hold audio-path
     * callbacks or grow the buffer without bound. Losing the oldest lifecycle event is
     * preferable to stalling the capture thread.
     */
    private val _events = MutableSharedFlow<VoiceEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<VoiceEvent> = _events.asSharedFlow()

    /**
     * Incremented on every open and on every close.
     *
     * This is how a late callback from a finished session is ignored. A focus change or route
     * change can arrive after cleanup -- the platform does not promise otherwise -- and
     * applying it would move a closed session back into Interrupted, or worse, reopen
     * resources. Each callback captures the generation it was registered for and does nothing
     * if the session has moved on. Mutual exclusion cannot solve this, because the stale
     * callback is perfectly entitled to the lock; only identity can.
     */
    @Volatile
    private var generation: Long = 0

    /** The route last reported by the platform, for consumers that want it without a callback. */
    @Volatile
    var currentRoute: VoiceAudioRoute = VoiceAudioRoute.UNKNOWN
        private set

    val effects: VoiceAudioEffectsStatus
        get() = when (val current = _state.value) {
            is VoiceSessionState.Ready -> current.effects
            is VoiceSessionState.Capturing -> current.effects
            else -> VoiceAudioEffectsStatus()
        }

    // ----------------------------------------------------------------- open / close

    /**
     * Acquire everything the session needs: permission check, focus, recorder, player, routing.
     *
     * Ordered so the cheapest and most likely refusal comes first and nothing is acquired
     * before it is known to be needed. Any failure releases whatever was already acquired --
     * the half-open session is the state that leaks a microphone.
     */
    suspend fun open(): VoiceAudioError? = mutex.withLock {
        if (!VoiceStateMachine.isAllowed(_state.value, VoiceStateMachine.Operation.OPEN)) {
            return@withLock fail(VoiceAudioError.IllegalTransition(_state.value.describe(), "open a session"))
        }

        // Checked live, every time. Permission can be revoked while the app is running, and the
        // spec requires a re-check after resume -- a value cached at construction would be a
        // lie by the time it mattered.
        if (!permission.isGranted()) {
            return@withLock fail(VoiceAudioError.PermissionDenied)
        }

        _state.value = VoiceSessionState.Preparing
        val myGeneration = ++generation

        if (!focus.request { hasFocus, transient -> onFocusChanged(myGeneration, hasFocus, transient) }) {
            releaseResources()
            return@withLock fail(VoiceAudioError.AudioFocusDenied)
        }

        // Native construction blocks, so it is kept off whatever thread the caller is on --
        // in practice the main thread, since sessions start from a tap.
        val prepareError = withContext(ioDispatcher) {
            capture.prepare(config) ?: playback.prepare(config)
        }
        if (prepareError != null) {
            releaseResources()
            return@withLock fail(prepareError)
        }

        // After prepare, before Ready: the track exists, so a route is meaningful, and the
        // student has not been told the session is usable yet.
        //
        // A routing failure does NOT fail the session. The platform may refuse a device
        // because another app owns it, and a conversation on the earpiece is worse than one
        // on the loudspeaker but far better than no conversation at all. It is surfaced as an
        // event so the UI can say so rather than being swallowed.
        val routingError = output.begin()
        if (routingError != null) {
            _events.tryEmit(VoiceEvent.OutputRoutingFailed(routingError))
        }

        routes.start { route -> onRouteChanged(myGeneration, route) }
        currentRoute = routes.currentRoute()

        val resolved = capture.effects()
        _state.value = VoiceSessionState.Ready(resolved)
        _events.tryEmit(VoiceEvent.SessionOpened)
        _events.tryEmit(VoiceEvent.EffectsResolved(resolved))
        _events.tryEmit(VoiceEvent.RouteChanged(currentRoute))
        null
    }

    /**
     * Release everything. Safe to call from any state, including [VoiceSessionState.Closed],
     * and safe to call twice.
     *
     * Idempotence is not politeness here: this is what a `finally` block and an `onCleared()`
     * both call, and a cleanup that throws because it already ran is a cleanup nobody dares
     * invoke from the path that matters.
     */
    suspend fun close() = mutex.withLock {
        if (_state.value is VoiceSessionState.Closed || _state.value is VoiceSessionState.Idle) {
            // Nothing held. Still bump the generation so any callback already in flight for a
            // previous session cannot apply.
            generation++
            _state.value = VoiceSessionState.Closed
            return@withLock
        }
        generation++
        withContext(ioDispatcher) { releaseResources() }
        _state.value = VoiceSessionState.Closed
        _events.tryEmit(VoiceEvent.SessionClosed)
    }

    /**
     * Release every native resource, in the reverse order of acquisition.
     *
     * Each step is independently guarded by the implementations' own runCatching, so one
     * failing release cannot prevent the others -- a half-released session holds a microphone
     * that no later call can reach.
     */
    private fun releaseResources() {
        routes.stop()
        capture.release()
        playback.release()
        // Before abandoning focus, and never conditional on how the session ended. The audio
        // mode is device-global: leaving it in MODE_IN_COMMUNICATION routes every other app's
        // audio to the earpiece long after this screen is gone, with nothing for the user to
        // connect it to. `end()` is documented not to throw, for exactly this path.
        output.end()
        focus.abandon()
    }

    /**
     * Move the assistant's voice to another output while the session is open.
     *
     * Exposed so a speaker control on the Voice Agent screen can act without reaching past
     * the session into `AudioManager`, which is what would let a UI change device-global state
     * that nothing then restores.
     *
     * Returns the error rather than throwing: a student tapping "Bluetooth" with no headset
     * paired has made an ordinary mistake, not triggered a fault.
     */
    suspend fun selectOutput(target: VoiceAudioRoute): VoiceAudioError? = mutex.withLock {
        val error = output.select(target)
        if (error == null) {
            currentRoute = output.activeOutput()
            _events.tryEmit(VoiceEvent.RouteChanged(currentRoute))
        }
        error
    }

    /** The outputs the platform will accept right now, for a speaker control to offer. */
    fun availableOutputs(): List<VoiceAudioRoute> = output.availableOutputs()

    // ----------------------------------------------------------------- capture

    /**
     * Start microphone capture, delivering frames to [sink].
     *
     * Permission is re-checked here as well as in [open], because a session can be open across
     * a trip to system settings where the user revokes it.
     */
    suspend fun startCapture(sink: PcmFrameSink): VoiceAudioError? = mutex.withLock {
        if (!VoiceStateMachine.isAllowed(_state.value, VoiceStateMachine.Operation.START_CAPTURE)) {
            return@withLock fail(
                VoiceAudioError.IllegalTransition(_state.value.describe(), "start recording")
            )
        }
        if (!permission.isGranted()) {
            return@withLock fail(VoiceAudioError.PermissionDenied)
        }

        val myGeneration = generation
        val wasPlaying = _state.value is VoiceSessionState.Playing
        val error = withContext(ioDispatcher) {
            capture.start { buffer, length ->
                // The generation check is what stops a frame from a previous session reaching
                // a sink that belongs to a newer one.
                if (myGeneration == generation) sink.onFrames(buffer, length)
            }
        }
        if (error != null) return@withLock fail(error)

        // Starting capture while already playing yields a duplex session rather than replacing
        // the playback state, so that stopping one direction later does not end both.
        _state.value = if (wasPlaying) {
            VoiceSessionState.Duplex(capture.effects())
        } else {
            VoiceSessionState.Capturing(capture.effects())
        }
        _events.tryEmit(VoiceEvent.CaptureStarted)
        null
    }

    /** Stop capture. A no-op when not capturing, so a caller need not track whether it started. */
    suspend fun stopCapture() = mutex.withLock {
        if (!VoiceStateMachine.isAllowed(_state.value, VoiceStateMachine.Operation.STOP_CAPTURE)) {
            return@withLock
        }
        val previous = _state.value
        val wasCapturing = previous is VoiceSessionState.Capturing ||
            previous is VoiceSessionState.Duplex
        val bytes = capture.bytesCaptured()
        withContext(ioDispatcher) { capture.stop() }

        if (wasCapturing) {
            // Leaving a duplex session drops back to playing, not to resting: the agent is
            // still speaking, and the user's turn ending must not cut it off.
            _state.value = if (previous is VoiceSessionState.Duplex) {
                VoiceSessionState.Playing
            } else {
                VoiceSessionState.Ready(capture.effects())
            }
            val frames = if (config.bytesPerFrame > 0) bytes / config.bytesPerFrame else 0
            _events.tryEmit(VoiceEvent.CaptureStopped(config.millisForBytes(bytes.toInt()), frames))
        }
    }

    // ----------------------------------------------------------------- playback

    suspend fun startPlayback(): VoiceAudioError? = mutex.withLock {
        if (!VoiceStateMachine.isAllowed(_state.value, VoiceStateMachine.Operation.START_PLAYBACK)) {
            return@withLock fail(
                VoiceAudioError.IllegalTransition(_state.value.describe(), "start playback")
            )
        }
        val wasCapturing = _state.value is VoiceSessionState.Capturing
        val error = withContext(ioDispatcher) { playback.start() }
        if (error != null) return@withLock fail(error)
        _state.value = if (wasCapturing) {
            VoiceSessionState.Duplex(capture.effects())
        } else {
            VoiceSessionState.Playing
        }
        _events.tryEmit(VoiceEvent.PlaybackStarted)
        null
    }

    /**
     * Write one chunk of PCM for playback.
     *
     * Not serialised on the mutex: this is called repeatedly from a producer loop and taking a
     * lock per chunk would let a writer block [stopPlayback], which is the one call that has
     * to be prompt. The underlying implementation already refuses writes once stopped.
     */
    suspend fun writePlayback(buffer: ByteArray, length: Int): VoiceAudioError? {
        val current = _state.value
        if (current !is VoiceSessionState.Playing && current !is VoiceSessionState.Duplex) {
            return null
        }
        return withContext(ioDispatcher) { playback.write(buffer, length) }
    }

    /**
     * Stop playback promptly, discarding anything buffered.
     *
     * This is the barge-in primitive. VA-2 will call it when the user starts speaking; VA-1's
     * job is only to guarantee that calling it stops audible output and that no further writes
     * are accepted. [cancelled] distinguishes "we cut it off" from "it finished", which the
     * conversation layer will need in order to reconcile a half-spoken turn.
     */
    suspend fun stopPlayback(cancelled: Boolean = true) = mutex.withLock {
        if (!VoiceStateMachine.isAllowed(_state.value, VoiceStateMachine.Operation.STOP_PLAYBACK)) {
            return@withLock
        }
        val previous = _state.value
        val wasPlaying = previous is VoiceSessionState.Playing ||
            previous is VoiceSessionState.Duplex
        withContext(ioDispatcher) { playback.stop() }
        if (wasPlaying) {
            // Barge-in stops the agent but must leave the microphone running: otherwise the
            // interrupting sentence is lost in the gap between stopping and restarting capture.
            _state.value = if (previous is VoiceSessionState.Duplex) {
                VoiceSessionState.Capturing(capture.effects())
            } else {
                VoiceSessionState.Ready(capture.effects())
            }
            _events.tryEmit(VoiceEvent.PlaybackStopped(cancelled))
        }
    }

    // ----------------------------------------------------------------- platform callbacks

    /**
     * Audio focus changed.
     *
     * Not `suspend` and does not take the mutex: it is called from the platform's own thread
     * and must not block there. Correctness comes from the generation check plus the fact that
     * the operations it performs are themselves idempotent and thread-safe.
     */
    private fun onFocusChanged(forGeneration: Long, hasFocus: Boolean, transient: Boolean) {
        if (forGeneration != generation) return // a previous session's callback
        _events.tryEmit(VoiceEvent.FocusChanged(hasFocus, transient))

        if (hasFocus) {
            // Only leave Interrupted. A session that was Ready stays Ready, and one the caller
            // closed stays closed -- regaining focus must not silently reopen the microphone.
            if (_state.value is VoiceSessionState.Interrupted) {
                _state.value = VoiceSessionState.Ready(capture.effects())
            }
            return
        }

        // Focus lost: stop both directions immediately rather than continuing under whatever
        // took focus. Continuing to hold the microphone through an interruption is exactly the
        // behaviour flagged as a gap.
        capture.stop()
        playback.stop()
        val reason = if (transient) {
            VoiceInterruption.AUDIO_FOCUS_LOST_TRANSIENT
        } else {
            VoiceInterruption.AUDIO_FOCUS_LOST
        }
        if (!VoiceStateMachine.isTerminal(_state.value)) {
            _state.value = VoiceSessionState.Interrupted(reason)
            _events.tryEmit(VoiceEvent.Interrupted(reason))
        }
    }

    private fun onRouteChanged(forGeneration: Long, route: VoiceAudioRoute) {
        if (forGeneration != generation) return
        currentRoute = route
        _events.tryEmit(VoiceEvent.RouteChanged(route))
    }

    // ----------------------------------------------------------------- helpers

    /** Record a failure as state and event, and return it so callers can `return fail(...)`. */
    private fun fail(error: VoiceAudioError): VoiceAudioError {
        // An unrecoverable error is terminal for the session; a recoverable one leaves the
        // session where it was so the caller can retry the same operation. Without this
        // distinction, a denied focus request would permanently kill a session that only
        // needed a second attempt.
        if (!error.isRecoverable) {
            _state.value = VoiceSessionState.Failed(error)
        } else if (_state.value is VoiceSessionState.Preparing) {
            // Nothing was successfully opened, so do not leave the session claiming to be
            // mid-preparation.
            _state.value = VoiceSessionState.Idle
        }
        _events.tryEmit(VoiceEvent.Failed(error))
        return error
    }

    private companion object {
        const val EVENT_BUFFER = 32
    }
}

/** A short name for a state, for error messages. */
internal fun VoiceSessionState.describe(): String = when (this) {
    is VoiceSessionState.Idle -> "idle"
    is VoiceSessionState.Preparing -> "preparing"
    is VoiceSessionState.Ready -> "ready"
    is VoiceSessionState.Capturing -> "already recording"
    is VoiceSessionState.Playing -> "playing audio"
    is VoiceSessionState.Duplex -> "recording and playing audio"
    is VoiceSessionState.Interrupted -> "interrupted"
    is VoiceSessionState.Failed -> "in a failed state"
    is VoiceSessionState.Closed -> "closed"
}
