package com.aiplacementos.app.ui.interview

import androidx.lifecycle.SavedStateHandle
import com.aiplacementos.app.data.remote.InterviewAnswerDto
import com.aiplacementos.app.data.remote.InterviewDto
import com.aiplacementos.app.data.remote.InterviewQuestionDto
import com.aiplacementos.app.data.remote.NextInterviewQuestionResponseDto
import com.aiplacementos.app.fakes.FakeInterviewRepository
import com.aiplacementos.app.voice.VoiceInterviewSession
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
 * D2: the ViewModel's recording lifecycle.
 *
 * What these prove: the ViewModel issues the right session commands for every lifecycle event,
 * never leaves the UI claiming to record after capture stopped, and never submits a take that
 * was cut short. The fake session records the command sequence, so the assertions are about
 * ordering and resulting state rather than about "a mock was called".
 *
 * What they deliberately do **not** prove, and must not be read as proving: that the production
 * `MediaRecorder` actually releases the microphone, closes its file, or survives an activity
 * recreation without leaving a second recorder behind. Those are properties of the Android audio
 * stack, they need instrumentation on a physical device, and they are tracked in
 * `VOICE_DEVICE_ACCEPTANCE_VA11.md` (rows A11, B5, D-1, D-2, D-4, D-5, G3). The session seam
 * exists so the testable half is tested deterministically, not to imply the rest is covered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceInterviewLifecycleTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Records what the ViewModel asked of the session, in order. */
    private class FakeSession : VoiceInterviewSession {
        val calls = mutableListOf<String>()
        var recording = false
        var submitted = 0
        var released = 0

        override val isRecording: Boolean get() = recording
        override var onInterrupted: ((reason: String) -> Unit)? = null

        override suspend fun playQuestionAudio(interviewId: Int, questionId: Int) {
            calls += "play($questionId)"
        }

        override fun startRecording(questionId: Int) {
            calls += "start($questionId)"
            recording = true
        }

        override suspend fun stopAndSubmitRecording(
            interviewId: Int,
            questionId: Int,
        ): InterviewAnswerDto {
            calls += "submit($questionId)"
            recording = false
            submitted++
            return InterviewAnswerDto(interviewQuestionId = questionId, answerText = "spoken answer")
        }

        override fun cancelRecording() {
            calls += "cancel"
            recording = false
        }

        override fun onLeftForeground() {
            calls += "leftForeground"
            if (recording) {
                recording = false
                onInterrupted?.invoke("left_foreground")
            }
        }

        override fun release() {
            calls += "release"
            recording = false
            released++
        }
    }

    private fun question(id: Int, order: Int = 1) = InterviewQuestionDto(
        id = id,
        orderIndex = order,
        questionText = "Question $id",
        basedOn = "DSA",
    )

    /** Serves a fresh question each time, so advancing a turn is observable by id. */
    private fun repo(questionIds: List<Int> = listOf(11, 12, 13)): FakeInterviewRepository {
        var served = 0
        return FakeInterviewRepository().apply {
            startResult = { type, mode, jdId ->
                InterviewDto(id = 7, type = type, mode = mode, jdId = jdId, status = "in_progress")
            }
            nextQuestionResult = {
                val id = questionIds.getOrNull(served)
                served++
                if (id == null) {
                    NextInterviewQuestionResponseDto(completed = true, question = null)
                } else {
                    NextInterviewQuestionResponseDto(
                        completed = false,
                        question = question(id, order = served),
                    )
                }
            }
        }
    }

    private fun viewModel(
        session: VoiceInterviewSession,
        repository: FakeInterviewRepository = repo(),
        handle: SavedStateHandle = SavedStateHandle(),
    ) = VoiceInterviewViewModel(
        jdId = 0,
        type = "technical",
        manager = session,
        interviewRepository = repository,
        savedStateHandle = handle,
    )

    private fun turn(state: VoiceInterviewUiState): VoiceInterviewUiState.Turn =
        state as VoiceInterviewUiState.Turn

    // ------------------------------------------------------------- the foreground policy

    @Test
    fun `leaving the foreground while recording stops capture`() = runTest(dispatcher) {
        val session = FakeSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()
        vm.startRecording()
        assertTrue(session.isRecording)

        vm.onLeftForeground()

        assertFalse("the microphone must not still be held", session.isRecording)
        assertTrue(session.calls.contains("leftForeground"))
    }

    @Test
    fun `leaving the foreground does not submit the cut-off take`() = runTest(dispatcher) {
        val session = FakeSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()
        vm.startRecording()

        vm.onLeftForeground()
        dispatcher.scheduler.advanceUntilIdle()

        // One answer per question, and a take cut off mid-sentence is not the student's answer:
        // storing it would spend the only submission that question gets.
        assertEquals(0, session.submitted)
    }

    @Test
    fun `the ui never claims to be recording after capture stopped`() = runTest(dispatcher) {
        val session = FakeSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()
        vm.startRecording()
        assertEquals(VoiceTurnPhase.RECORDING, turn(vm.uiState.value).phase)

        vm.onLeftForeground()

        assertEquals(VoiceTurnPhase.READY_TO_RECORD, turn(vm.uiState.value).phase)
    }

    @Test
    fun `leaving the foreground when not recording is harmless`() = runTest(dispatcher) {
        val session = FakeSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()
        val before = turn(vm.uiState.value).phase

        vm.onLeftForeground()

        assertEquals(before, turn(vm.uiState.value).phase)
        assertEquals(0, session.submitted)
    }

    @Test
    fun `repeated foreground exits are idempotent`() = runTest(dispatcher) {
        val session = FakeSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()
        vm.startRecording()

        vm.onLeftForeground()
        vm.onLeftForeground()
        vm.onLeftForeground()

        assertFalse(session.isRecording)
        assertEquals(VoiceTurnPhase.READY_TO_RECORD, turn(vm.uiState.value).phase)
    }

    // ------------------------------------------------------------- interruption (D1)

    @Test
    fun `an interruption reported by the session is reflected in the ui`() = runTest(dispatcher) {
        val session = FakeSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()
        vm.startRecording()

        // What a real audio-focus loss does: the session stops capture, then reports it.
        session.recording = false
        session.onInterrupted?.invoke("audio_focus_lost")

        assertEquals(VoiceTurnPhase.READY_TO_RECORD, turn(vm.uiState.value).phase)
    }

    @Test
    fun `a stale interruption cannot disturb a turn that is not recording`() = runTest(dispatcher) {
        val session = FakeSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()
        val before = turn(vm.uiState.value)

        // Arrives after the capture it belonged to already ended.
        session.onInterrupted?.invoke("audio_focus_lost")

        assertEquals(before.phase, turn(vm.uiState.value).phase)
        assertEquals(before.question.id, turn(vm.uiState.value).question.id)
    }

    // ------------------------------------------------------------- recreation

    @Test
    fun `recreation with a saved interview resumes it instead of starting a second one`() =
        runTest(dispatcher) {
            val repository = repo()
            val handle = SavedStateHandle(mapOf("interviewId" to 7))

            viewModel(FakeSession(), repository, handle)
            dispatcher.scheduler.advanceUntilIdle()

            // The rotation case: a recreated ViewModel must not create a second interview and
            // abandon the one the student is halfway through.
            assertTrue(repository.keys.isEmpty())
            assertEquals(null, repository.lastStartArgs)
        }

    @Test
    fun `recreation does not start a recorder on its own`() = runTest(dispatcher) {
        val session = FakeSession()

        viewModel(session, repo(), SavedStateHandle(mapOf("interviewId" to 7)))
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(session.isRecording)
        assertTrue(
            "recreation must not begin capture by itself",
            session.calls.none { it.startsWith("start(") },
        )
    }

    // ------------------------------------------------------------- ordinary turns

    @Test
    fun `cancelling returns to ready and submits nothing`() = runTest(dispatcher) {
        val session = FakeSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()
        vm.startRecording()

        vm.cancelRecording()

        assertEquals(VoiceTurnPhase.READY_TO_RECORD, turn(vm.uiState.value).phase)
        assertTrue(session.calls.contains("cancel"))
        assertEquals(0, session.submitted)
    }

    @Test
    fun `a submitted answer advances to the next question exactly once`() = runTest(dispatcher) {
        val session = FakeSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()
        val first = turn(vm.uiState.value).question.id
        vm.startRecording()

        vm.stopRecordingAndSubmit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, session.submitted)
        assertTrue(turn(vm.uiState.value).question.id != first)
    }

    @Test
    fun `submitting is refused unless a recording is in progress`() = runTest(dispatcher) {
        val session = FakeSession()
        val vm = viewModel(session)
        dispatcher.scheduler.advanceUntilIdle()

        vm.stopRecordingAndSubmit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, session.submitted)
    }

    @Test
    fun `a completed interview releases the session`() = runTest(dispatcher) {
        val session = FakeSession()
        // One question, then completion.
        val vm = viewModel(session, repo(listOf(11)))
        dispatcher.scheduler.advanceUntilIdle()
        vm.startRecording()

        vm.stopRecordingAndSubmit()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value is VoiceInterviewUiState.Completed)
        assertTrue("a finished interview must not keep audio resources", session.released > 0)
    }
}
