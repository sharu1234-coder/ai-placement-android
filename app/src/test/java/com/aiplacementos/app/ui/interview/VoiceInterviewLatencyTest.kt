package com.aiplacementos.app.ui.interview

import androidx.lifecycle.SavedStateHandle
import com.aiplacementos.app.data.remote.InterviewAnswerDto
import com.aiplacementos.app.data.remote.InterviewDto
import com.aiplacementos.app.data.remote.InterviewQuestionDto
import com.aiplacementos.app.data.remote.NextInterviewQuestionResponseDto
import com.aiplacementos.app.fakes.FakeInterviewRepository
import com.aiplacementos.app.voice.VoiceInterviewSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The question is read aloud *beside* the student, not in front of them.
 *
 * ## The defect
 *
 * A student reported the voice interview as slow to load. It was not loading. `loadNextTurn`
 * held the turn in a phase that disabled every control until `playQuestionAudio` returned, and
 * that call suspends until the question has finished **playing**. Measured on 2026-10-18 against
 * the real local synthesiser, a typical interview question comes back as 6.25 seconds of audio
 * (22.05 kHz, mono, 16-bit, 275,590 bytes) and synthesises in 0.43s cold / 0.13s warm -- so
 * essentially the entire wait was the question being spoken, with a dead "Playing..." button in
 * front of it, once per question.
 *
 * These tests pin the fixed behaviour: the controls are live while the audio plays, starting to
 * record stops the audio, and nothing about the audio can block or fail the interview.
 *
 * ## What they do not prove
 *
 * Nothing here measures latency. They prove the *ordering* that removes the wait -- that the
 * state advances without awaiting playback. The 6.25s figure above came from running the
 * synthesiser, not from this suite, and no timing assertion appears below, because a wall-clock
 * assertion on a test dispatcher would measure nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceInterviewLatencyTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /**
     * A session whose question playback lasts exactly as long as the test says.
     *
     * The previous fake returned from `playQuestionAudio` immediately, which is why the old
     * blocking behaviour was invisible to the existing suite: with instant playback there is no
     * window in which the student is blocked, so the bug could not be observed. Holding playback
     * open is what makes the window real.
     */
    private class SlowSpeakingSession : VoiceInterviewSession {
        val playing = mutableListOf<Int>()
        var finish = CompletableDeferred<Unit>()
        var failWith: Exception? = null
        var cancelled = 0
        var recordingQuestion: Int? = null

        override val isRecording: Boolean get() = recordingQuestion != null
        override var onInterrupted: ((reason: String) -> Unit)? = null

        override suspend fun playQuestionAudio(interviewId: Int, questionId: Int) {
            playing += questionId
            failWith?.let { throw it }
            try {
                finish.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled++
                throw e
            }
        }

        override fun startRecording(questionId: Int) {
            recordingQuestion = questionId
        }

        override suspend fun stopAndSubmitRecording(
            interviewId: Int,
            questionId: Int,
        ): InterviewAnswerDto {
            recordingQuestion = null
            return InterviewAnswerDto(interviewQuestionId = questionId, answerText = "spoken")
        }

        override fun cancelRecording() {
            recordingQuestion = null
        }

        override fun onLeftForeground() {
            recordingQuestion = null
        }

        override fun release() {
            recordingQuestion = null
        }
    }

    private fun repo() = FakeInterviewRepository().apply {
        startResult = { type, mode, jdId ->
            InterviewDto(id = 7, type = type, mode = mode, jdId = jdId, status = "in_progress")
        }
        nextQuestionResult = {
            NextInterviewQuestionResponseDto(
                completed = false,
                question = InterviewQuestionDto(
                    id = 11,
                    orderIndex = 1,
                    questionText = "Walk me through a project you are proud of.",
                    basedOn = "Resume",
                ),
            )
        }
    }

    private fun viewModel(session: VoiceInterviewSession) = VoiceInterviewViewModel(
        jdId = 0,
        type = "technical",
        manager = session,
        interviewRepository = repo(),
        savedStateHandle = SavedStateHandle(),
    )

    private fun turn(vm: VoiceInterviewViewModel) = vm.uiState.value as VoiceInterviewUiState.Turn

    // ----------------------------------------------------------------- the fix

    @Test
    fun `the question is on screen while it is still being spoken`() = runTest(dispatcher) {
        val session = SlowSpeakingSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()

        // Playback has started and has NOT finished -- the deferred is still open.
        assertEquals(listOf(11), session.playing)
        assertFalse("playback must still be in progress for this test to mean anything", session.finish.isCompleted)

        val state = turn(vm)
        assertEquals(VoiceTurnPhase.SPEAKING, state.phase)
        assertEquals(
            "the question text must already be available to read",
            "Walk me through a project you are proud of.",
            state.question.questionText,
        )
    }

    @Test
    fun `recording can start while the question is still being spoken`() = runTest(dispatcher) {
        val session = SlowSpeakingSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(VoiceTurnPhase.SPEAKING, turn(vm).phase)

        vm.startRecording()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(VoiceTurnPhase.RECORDING, turn(vm).phase)
        assertEquals("the microphone must be open on this question", 11, session.recordingQuestion)
        // The question must stop playing, or it is recorded into the student's own answer.
        assertEquals("starting to record must cancel playback", 1, session.cancelled)
    }

    @Test
    fun `playback finishing after recording started does not reset the phase`() = runTest(dispatcher) {
        /*
         * The regression this pins. `advancePastAudio` runs when playback unwinds, including
         * when it unwinds because it was cancelled. Without its phase guard, a student who
         * started talking over the question would be thrown back to "ready to record" --
         * discarding a take they were in the middle of giving.
         */
        val session = SlowSpeakingSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()
        vm.startRecording()
        dispatcher.scheduler.advanceUntilIdle()

        session.finish.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(VoiceTurnPhase.RECORDING, turn(vm).phase)
        assertEquals(11, session.recordingQuestion)
    }

    @Test
    fun `when the question finishes playing the turn becomes ready to record`() = runTest(dispatcher) {
        val session = SlowSpeakingSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()

        session.finish.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        val state = turn(vm)
        assertEquals(VoiceTurnPhase.READY_TO_RECORD, state.phase)
        assertFalse("audio played fine, so nothing should be reported", state.audioUnavailable)
    }

    @Test
    fun `skipping the audio opens recording immediately`() = runTest(dispatcher) {
        val session = SlowSpeakingSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()

        vm.skipQuestionAudio()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(VoiceTurnPhase.READY_TO_RECORD, turn(vm).phase)
        assertEquals("skipping must stop the audio, not just hide it", 1, session.cancelled)
        // Still open: the point is that the student did not wait for it.
        assertFalse(session.finish.isCompleted)
    }

    // ----------------------------------------------------------------- audio is not the interview

    @Test
    fun `a synthesis failure leaves the question answerable`() = runTest(dispatcher) {
        /*
         * Before the fix, `playQuestionAudio` was awaited inside `loadNextTurn`'s try block, so
         * any synthesis failure -- a missing TTS driver on the host, for instance -- threw past
         * it and put the whole interview on an error screen. The question was perfectly
         * readable; only the convenience of hearing it had failed.
         */
        val session = SlowSpeakingSession()
        session.failWith = IllegalStateException("no speech driver")
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(
            "a failure to speak the question must not end the interview, but state was " + state,
            state is VoiceInterviewUiState.Turn,
        )
        state as VoiceInterviewUiState.Turn
        assertEquals(VoiceTurnPhase.READY_TO_RECORD, state.phase)
        assertTrue("the student must be told why it went quiet", state.audioUnavailable)
    }

    @Test
    fun `leaving the screen while the question is speaking stops the audio`() = runTest(dispatcher) {
        val session = SlowSpeakingSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onLeftForeground()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "the app must not keep talking after the student has left the screen",
            1,
            session.cancelled,
        )
        assertEquals(VoiceTurnPhase.READY_TO_RECORD, turn(vm).phase)
    }
}
