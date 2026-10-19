package com.aiplacementos.app.ui.voiceagent

import com.aiplacementos.app.voice.core.FakeCapture
import com.aiplacementos.app.voice.core.FakeFocus
import com.aiplacementos.app.voice.core.FakePermission
import com.aiplacementos.app.voice.core.FakePlayback
import com.aiplacementos.app.voice.core.FakeRoutes
import com.aiplacementos.app.voice.core.VoiceAudioConfig
import com.aiplacementos.app.voice.core.VoiceSession
import com.aiplacementos.app.voice.realtime.ConversationState
import com.aiplacementos.app.voice.realtime.FakeRealtimeTransport
import com.aiplacementos.app.voice.realtime.InterruptionReason
import com.aiplacementos.app.voice.realtime.InterviewContext
import com.aiplacementos.app.voice.realtime.RealtimeClientEvent
import com.aiplacementos.app.voice.realtime.RealtimeError
import com.aiplacementos.app.voice.realtime.RealtimeServerEvent
import com.aiplacementos.app.voice.realtime.RealtimeVoiceSession
import com.aiplacementos.app.voice.realtime.VoiceAgentMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Voice Agent ViewModel: state handling, lifecycle ownership, and what it refuses to claim.
 *
 * **What is real:** the real `VoiceAgentViewModel` and its reducer, and a real
 * `RealtimeVoiceSession` -- so the conversation state machine, the generation counters and the
 * event routing are all the production ones.
 *
 * **What is faked:** the transport and the audio hardware, through the fakes VA-1 and VA-2
 * already established (`FakeRealtimeTransport`, `FakeCapture`, `FakePlayback`, …) rather than a
 * second set written here. So these tests prove what the screen does with events and how it
 * manages the session's lifetime. They prove **nothing** about microphone capture, playback,
 * recognition, synthesis, or a conversation with a real server. Physical-device acceptance is
 * BLOCKED and recorded as such; the backend end-to-end evidence lives in the backend suite.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceAgentViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** The audio stack, faked; the conversation layer above it is real. */
    private class Harness(dispatcher: kotlin.coroutines.CoroutineContext) {
        val config = VoiceAudioConfig()
        val transport = FakeRealtimeTransport()
        val scope = CoroutineScope(dispatcher)
        val audio = VoiceSession(
            config = config,
            permission = FakePermission(),
            focus = FakeFocus(),
            capture = FakeCapture(),
            playback = FakePlayback(),
            routes = FakeRoutes(),
            ioDispatcher = dispatcher as kotlinx.coroutines.CoroutineDispatcher,
        )

        fun session(scope: CoroutineScope, mode: VoiceAgentMode, interview: InterviewContext?) =
            RealtimeVoiceSession(
                audio = audio,
                transport = transport,
                config = config,
                scope = scope,
                mode = mode,
                interview = interview,
            )
    }

    private class Fixture(
        val viewModel: VoiceAgentViewModel,
        val transport: FakeRealtimeTransport,
        val built: MutableList<RealtimeVoiceSession>,
    )

    private fun fixture(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        mode: VoiceAgentMode = VoiceAgentMode.COACH,
        interview: InterviewContext? = null,
    ): Fixture {
        val harness = Harness(dispatcher)
        val built = mutableListOf<RealtimeVoiceSession>()
        val vm = VoiceAgentViewModel(
            mode = mode,
            interview = interview,
            sessionFactory = { scope -> harness.session(scope, mode, interview).also { built += it } },
        )
        return Fixture(vm, harness.transport, built)
    }

    // ------------------------------------------------------------------ permission

    @Test
    fun `a session cannot start without microphone permission`() = runTest {
        val f = fixture(UnconfinedTestDispatcher(testScheduler))

        f.viewModel.start()
        advanceUntilIdle()

        assertTrue("a session was built with no permission granted", f.built.isEmpty())
        assertFalse(f.viewModel.uiState.value.canStart)
    }

    @Test
    fun `revoking permission ends a live session`() = runTest {
        val f = fixture(UnconfinedTestDispatcher(testScheduler))
        f.viewModel.onMicPermission(true)
        f.viewModel.start()
        advanceUntilIdle()

        f.viewModel.onMicPermission(false)
        advanceUntilIdle()

        assertEquals("the socket outlived the permission that justified it", 1, f.transport.disconnects)
    }

    // ------------------------------------------------------------------ the turn

    @Test
    fun `a transcript and an answer land on the same turn`() = runTest {
        val f = started(testScheduler)

        f.transport.emit(RealtimeServerEvent.FinalTranscript("t-1", "Which skills should I work on?"))
        f.transport.emit(RealtimeServerEvent.AssistantMessage("t-1", "Work on DSA next.", spoken = true))
        advanceUntilIdle()

        val turn = f.viewModel.uiState.value.turns.single()
        assertEquals("Which skills should I work on?", turn.transcript)
        assertEquals("Work on DSA next.", turn.answer)
        assertTrue(turn.spoken)
    }

    @Test
    fun `an answer that could not be spoken is rendered and marked unspoken`() = runTest {
        val f = started(testScheduler)

        f.transport.emit(RealtimeServerEvent.AssistantMessage("t-2", "Read this instead.", spoken = false))
        advanceUntilIdle()

        val turn = f.viewModel.uiState.value.turns.single()
        assertEquals("Read this instead.", turn.answer)
        assertFalse("the UI would wait for audio that is never coming", turn.spoken)
    }

    @Test
    fun `a failed turn is not a failed session`() = runTest {
        val f = started(testScheduler)

        f.transport.emit(RealtimeServerEvent.TurnFailed("t-3", "nothing_recognised", "I did not hear anything."))
        advanceUntilIdle()

        val state = f.viewModel.uiState.value
        assertEquals("nothing_recognised", state.turns.single().failure?.code)
        assertEquals("I did not hear anything.", state.turns.single().failure?.message)
        // A session notice is for session-level problems. A mumble is not one, and escalating it
        // would end a conversation over a cough.
        assertNull("a per-turn failure was escalated to a session failure", state.notice)
    }

    @Test
    fun `a turn the server has said nothing about is not rendered at all`() = runTest {
        val f = started(testScheduler)
        assertTrue(f.viewModel.uiState.value.turns.isEmpty())
    }

    @Test
    fun `a partial transcript is never recorded as what the student said`() = runTest {
        // The backend has no `partial_transcript` builder: the only approved recogniser is
        // turn-based, so a partial could only be manufactured. The decoder branch stays for a
        // future streaming recogniser, and must not write a provisional string in as if final.
        val f = started(testScheduler)

        f.transport.emit(RealtimeServerEvent.PartialTranscript("t-4", "which skills sh"))
        advanceUntilIdle()

        assertTrue(f.viewModel.uiState.value.turns.isEmpty())
    }

    // ------------------------------------------------------------------ notices

    @Test
    fun `an expired session is reported as expired rather than as a generic failure`() = runTest {
        val f = started(testScheduler)

        f.transport.emit(RealtimeServerEvent.SessionExpired)
        advanceUntilIdle()

        assertEquals(VoiceAgentNotice.SessionExpired, f.viewModel.uiState.value.notice)
    }

    @Test
    fun `an operation whose outcome is unknown is surfaced and never retried`() = runTest {
        val f = started(testScheduler)
        f.built.single().trackOperation("op-1", "Save Backend Intern at Acme")

        f.viewModel.stop()
        advanceUntilIdle()

        // The notice has to survive the teardown that produces it. A generation bump in `stop()`
        // would drop it, which is how a student whose confirmed action was in flight when they
        // hung up ends up being told nothing at all -- the one case where silence is worst.
        val notice = f.viewModel.uiState.value.notice
        assertTrue("an unknown outcome was not surfaced", notice is VoiceAgentNotice.OperationUnknown)
        assertTrue(
            "the notice must name the operation so the student knows what to check",
            notice!!.message.contains("Save Backend Intern at Acme"),
        )
        // Nothing was re-sent on the student's behalf. Re-running a mutation whose result is
        // unknown is the one thing a reconnect must never do.
        assertTrue(f.transport.sent.none { it is RealtimeClientEvent.CancelOperation })
    }

    @Test
    fun `a notice can be dismissed`() = runTest {
        val f = started(testScheduler)
        f.transport.emit(RealtimeServerEvent.SessionExpired)
        advanceUntilIdle()

        f.viewModel.dismissNotice()

        assertNull(f.viewModel.uiState.value.notice)
    }

    @Test
    fun `a rejected credential is not offered as retryable`() = runTest {
        val f = fixture(UnconfinedTestDispatcher(testScheduler))
        f.transport.connectError = RealtimeError.AuthFailed
        f.viewModel.onMicPermission(true)

        f.viewModel.start()
        advanceUntilIdle()

        val notice = f.viewModel.uiState.value.notice
        assertTrue("a connect failure was not reported", notice is VoiceAgentNotice.Failed)
        assertFalse(
            "retrying a rejected credential loops forever",
            (notice as VoiceAgentNotice.Failed).recoverable,
        )
    }

    // ------------------------------------------------------------------ lifecycle (D2)

    @Test
    fun `leaving the foreground releases the session`() = runTest {
        val f = started(testScheduler)

        f.viewModel.onLeftForeground()
        advanceUntilIdle()

        assertEquals("the microphone was still held in the background", 1, f.transport.disconnects)
        assertEquals(ConversationState.Closed, f.viewModel.uiState.value.conversation)
    }

    @Test
    fun `a late event from a replaced session cannot change the live one`() = runTest {
        val f = started(testScheduler)
        // A real socket does call back after its replacement is live. Captured before the swap,
        // which is the only way to deliver through a connection that is already gone.
        val stale = f.transport.capturedListener()!!

        f.viewModel.stop()
        advanceUntilIdle()
        f.viewModel.start()
        advanceUntilIdle()

        f.transport.emitThrough(
            stale,
            RealtimeServerEvent.AssistantMessage("stale-turn", "an answer nobody waited for", spoken = true),
        )
        advanceUntilIdle()

        assertTrue(
            "a reply from a session the student already ended reached the live UI",
            f.viewModel.uiState.value.turns.none { it.turnId == "stale-turn" },
        )
    }

    @Test
    fun `starting twice does not open a second session`() = runTest {
        val f = fixture(UnconfinedTestDispatcher(testScheduler))
        f.viewModel.onMicPermission(true)

        f.viewModel.start()
        advanceUntilIdle()
        f.viewModel.start()
        advanceUntilIdle()

        assertEquals("a second microphone and socket were opened", 1, f.built.size)
    }

    @Test
    fun `stopping is safe with nothing running`() = runTest {
        val f = fixture(UnconfinedTestDispatcher(testScheduler))
        f.viewModel.stop()
        f.viewModel.onLeftForeground()
        assertEquals(0, f.transport.disconnects)
    }

    // ------------------------------------------------------------------ cancellation

    @Test
    fun `interrupting asks the server to abandon the generation`() = runTest {
        val f = started(testScheduler)
        f.transport.emit(RealtimeServerEvent.GenerationStarted("g-1"))
        advanceUntilIdle()

        f.viewModel.interrupt(InterruptionReason.USER_STOPPED)
        advanceUntilIdle()

        val interrupts = f.transport.interrupts()
        assertEquals(1, interrupts.size)
        // Naming the generation is what stops the server cancelling the turn *after* this one.
        assertEquals("g-1", interrupts.single().generationId)
    }

    @Test
    fun `an interrupt is reported to the student as an interruption`() = runTest {
        val f = started(testScheduler)
        f.transport.emit(RealtimeServerEvent.GenerationStarted("g-2"))
        advanceUntilIdle()

        f.viewModel.interrupt(InterruptionReason.USER_STOPPED)
        advanceUntilIdle()

        assertTrue(f.viewModel.uiState.value.notice is VoiceAgentNotice.Interrupted)
    }

    // ------------------------------------------------------------------ mode

    @Test
    fun `coach mode negotiates the coach path and sends no interview ids`() = runTest {
        val f = started(testScheduler, VoiceAgentMode.COACH)

        val config = f.transport.lastConfig!!
        assertEquals(VoiceAgentMode.COACH, config.mode)
        assertNull("coach mode must not name an interview", config.interview)
    }

    @Test
    fun `interview mode carries the context the server validates before accepting`() = runTest {
        val context = InterviewContext(interviewId = 7, firstQuestionId = 11)
        val f = started(testScheduler, VoiceAgentMode.INTERVIEW, context)

        val config = f.transport.lastConfig!!
        assertEquals(VoiceAgentMode.INTERVIEW, config.mode)
        assertEquals(context, config.interview)
    }

    @Test
    fun `the session id is recorded once the server accepts`() = runTest {
        val f = started(testScheduler)

        f.transport.emit(RealtimeServerEvent.SessionAccepted("s-9", "r-9"))
        advanceUntilIdle()

        assertEquals("s-9", f.viewModel.uiState.value.sessionId)
    }

    // ------------------------------------------------------------------ helpers

    private fun kotlinx.coroutines.test.TestScope.started(
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
        mode: VoiceAgentMode = VoiceAgentMode.COACH,
        interview: InterviewContext? = null,
    ): Fixture {
        val f = fixture(UnconfinedTestDispatcher(scheduler), mode, interview)
        f.viewModel.onMicPermission(true)
        f.viewModel.start()
        advanceUntilIdle()
        return f
    }
}
