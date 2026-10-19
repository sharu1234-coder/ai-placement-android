package com.aiplacementos.app.ui.voiceagent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.voice.realtime.ConversationError
import com.aiplacementos.app.voice.realtime.ConversationState
import com.aiplacementos.app.voice.realtime.InterruptionReason
import com.aiplacementos.app.voice.realtime.InterviewContext
import com.aiplacementos.app.voice.realtime.RealtimeEvent
import com.aiplacementos.app.voice.realtime.RealtimeVoiceSession
import com.aiplacementos.app.voice.realtime.VoiceAgentMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns one realtime voice conversation for as long as the screen exists.
 *
 * ## D2: why the session lives here and nowhere else
 *
 * A `RealtimeVoiceSession` holds a microphone, an `AudioTrack`, a WebSocket and a coroutine
 * scope. The bug D2 recorded was a voice object created by `remember { }` inside a composable:
 * an activity recreation dropped the reference while the recorder was still live, so the
 * microphone stayed open and nothing ever released it. A rotation was enough to do it.
 *
 * So the session is built **once**, lazily, from `viewModelScope`, and the ViewModel survives
 * configuration changes. Nothing here holds an `Activity`, a `Context` or a composable scope --
 * the factory that builds the session is application-scoped and takes the scope as a parameter.
 * The only teardown path is [onCleared], which fires when the screen is genuinely gone rather
 * than when it is merely recreated.
 *
 * ## Stale callbacks
 *
 * Every mutation of [uiState] runs on `viewModelScope`, and [sessionGeneration] rises each time
 * a session is started or replaced. An event from a session this ViewModel has already replaced
 * is dropped rather than applied, which is what stops a reply from an abandoned conversation
 * appearing in a new one. `RealtimeVoiceSession` has its own generation counters for the socket;
 * this one guards the *UI* against a session swap, which is a different boundary.
 *
 * ## What this class does not do
 *
 * It does not grade, classify, choose tools, decide a mode per turn, or interpret a transcript.
 * The mode is fixed when the session opens. A spoken sentence is rendered and nothing else: the
 * confirmation flow is driven by explicit taps through `ActionConfirmationHost`, never by text
 * the model produced or by the student saying "yes" -- see [VoiceAgentScreen].
 */
class VoiceAgentViewModel(
    private val mode: VoiceAgentMode,
    private val interview: InterviewContext? = null,
    /**
     * Injected rather than constructed, which is the seam the tests use. A fake supplies a
     * session without a microphone, a socket or a server; what it cannot prove is anything about
     * real audio, and the test that uses it says so.
     */
    private val sessionFactory: (CoroutineScope) -> RealtimeVoiceSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceAgentUiState(mode = mode))
    val uiState: StateFlow<VoiceAgentUiState> = _uiState.asStateFlow()

    private var session: RealtimeVoiceSession? = null
    private var sessionGeneration = 0

    fun onMicPermission(granted: Boolean) {
        _uiState.value = _uiState.value.copy(micGranted = granted)
        if (!granted) {
            // Dropped rather than left running: a session whose capture has been revoked
            // produces silence, and silence committed as a turn is a turn the server charges
            // for and answers with "I did not hear anything".
            stop()
        }
    }

    /** Open a conversation. Idempotent while one is live. */
    fun start() {
        if (_uiState.value.isLive || !_uiState.value.micGranted) return
        val generation = ++sessionGeneration
        val live = sessionFactory(viewModelScope).also { session = it }

        viewModelScope.launch { observe(live, generation) }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                conversation = ConversationState.Connecting,
                notice = null,
            )
            val error = live.start()
            if (generation != sessionGeneration) return@launch
            if (error != null) {
                _uiState.value = _uiState.value.copy(
                    notice = VoiceAgentNotice.Failed(error.message, error.isRecoverable),
                )
            }
        }
    }

    /**
     * Barge-in. The student spoke over the agent, or tapped stop.
     *
     * Cancellation is honest about its reach: this stops playback locally and asks the server to
     * abandon the generation, but a recogniser or a speech driver already running inside a
     * worker thread on the server cannot be interrupted. Its result is simply never sent.
     */
    fun interrupt(reason: InterruptionReason = InterruptionReason.USER_STOPPED) {
        val live = session ?: return
        viewModelScope.launch { live.interrupt(reason) }
    }

    /**
     * End the conversation and release the microphone.
     *
     * **The generation is deliberately not bumped here.** Tearing a session down is when it
     * reports its *most* important events: `RealtimeVoiceSession.stop()` marks every in-flight
     * operation UNKNOWN and emits one notice per operation, because once the connection is gone
     * there is no way left to learn what happened. An earlier version of this method incremented
     * the counter first, as [start] does, and so discarded exactly those notices -- a student
     * whose "save this job" was in flight when they hung up was told nothing at all.
     *
     * The counter exists to reject events from a session this ViewModel has *replaced*, which is
     * a different thing. [start] bumps it, and that is what orphans the old one.
     */
    fun stop() {
        val live = session ?: return
        session = null
        viewModelScope.launch { live.stop() }
        _uiState.value = _uiState.value.copy(conversation = ConversationState.Closed)
    }

    /**
     * The screen is no longer in front of the student.
     *
     * Same choice as the file-based voice interview, for the same reason: `ON_STOP`, not
     * `ON_PAUSE`, so a permission dialog or a notification shade does not end a conversation
     * that is still legitimately happening -- but anything that actually backgrounds the app
     * does. A voice session that kept the microphone open in the background would be both a
     * privacy problem and a battery one.
     */
    fun onLeftForeground() = stop()

    fun dismissNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    override fun onCleared() {
        // The screen is gone for good, not merely recreated. This is the one teardown path, and
        // it is why the session is owned here instead of in a composable.
        session?.let { live -> CoroutineScope(viewModelScope.coroutineContext).launch { live.stop() } }
        session = null
        super.onCleared()
    }

    // ----------------------------------------------------------------- events

    private suspend fun observe(live: RealtimeVoiceSession, generation: Int) {
        viewModelScope.launch {
            live.state.collect { state ->
                if (generation != sessionGeneration) return@collect
                _uiState.value = _uiState.value.copy(conversation = state)
            }
        }
        live.events.collect { event ->
            if (generation != sessionGeneration) return@collect
            apply(event, live)
        }
    }

    private fun apply(event: RealtimeEvent, live: RealtimeVoiceSession) {
        val current = _uiState.value
        _uiState.value = when (event) {
            is RealtimeEvent.Connected ->
                current.copy(sessionId = event.sessionId, notice = null)

            RealtimeEvent.Reconnected -> current.copy(notice = null)

            // The student's own words. Creates the turn if this is the first thing heard about
            // it, which it normally is.
            is RealtimeEvent.FinalTranscript ->
                current.withTurn(event.turnId) { it.copy(transcript = event.text) }

            // Deliberately handled and deliberately never produced by our server: the only
            // approved recogniser is turn-based, so `partial_transcript` has no builder on the
            // backend at all. Rendered as a provisional transcript rather than dropped, so a
            // future streaming recogniser needs no change here -- and never recorded as final.
            is RealtimeEvent.PartialTranscript -> current

            is RealtimeEvent.AssistantMessage ->
                current.withTurn(event.turnId) {
                    it.copy(answer = event.text, spoken = event.spoken)
                }

            is RealtimeEvent.TurnFailed ->
                current.withTurn(event.turnId) {
                    it.copy(failure = VoiceAgentTurnFailure(event.code, event.message))
                }

            is RealtimeEvent.Interrupted ->
                current.copy(notice = VoiceAgentNotice.Interrupted(event.reason))

            is RealtimeEvent.OperationUnknown -> current.copy(
                notice = VoiceAgentNotice.OperationUnknown(event.description),
                unresolvedOperations = live.unresolvedOperations(),
            )

            is RealtimeEvent.OperationResolved ->
                current.copy(unresolvedOperations = live.unresolvedOperations())

            is RealtimeEvent.Failed -> current.copy(
                notice = if (event.error is ConversationError.SessionExpired) {
                    VoiceAgentNotice.SessionExpired
                } else {
                    VoiceAgentNotice.Failed(event.error.message, event.error.isRecoverable)
                },
            )

            RealtimeEvent.Closed -> current
            is RealtimeEvent.UserTurnEnded -> current
            is RealtimeEvent.BackchannelIgnored -> current
            is RealtimeEvent.BargeIn -> current
        }
    }
}

/**
 * Update the turn with this id, appending it if it is new.
 *
 * Turn ids come from the server and arrive in stage order, so the first event for a turn creates
 * it and later ones fill it in. A bounded list: older turns beyond [MAX_RENDERED_TURNS] are
 * dropped from the *view*, which is a rendering limit and not a claim that they did not happen.
 */
private fun VoiceAgentUiState.withTurn(
    turnId: String,
    update: (VoiceAgentTurn) -> VoiceAgentTurn,
): VoiceAgentUiState {
    val index = turns.indexOfFirst { it.turnId == turnId }
    val next = if (index >= 0) {
        turns.toMutableList().also { it[index] = update(it[index]) }
    } else {
        (turns + update(VoiceAgentTurn(turnId))).takeLast(MAX_RENDERED_TURNS)
    }
    return copy(turns = next)
}

/** Enough for a long conversation on screen without holding every turn of a half-hour session. */
private const val MAX_RENDERED_TURNS = 50
