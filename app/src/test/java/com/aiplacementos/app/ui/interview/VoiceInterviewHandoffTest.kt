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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The hand-off from the file-based voice interview to the realtime Interview Coach.
 *
 * What this is about: the realtime agent answers to the *same* interview engine, through
 * `POST /interviews/{id}/voice-turn`. So the only thing the client contributes is a pair of
 * identifiers and, afterwards, a willingness to believe the server rather than itself. Both
 * halves can fail quietly:
 *
 * - send the wrong pair and the gateway refuses the socket -- on a device, nowhere else;
 * - come back and redraw the remembered question, and the student is invited to answer a
 *   question they already answered aloud. Nothing crashes; the interview just goes wrong.
 *
 * So these tests pin which ids leave the screen and what a return re-reads. They prove nothing
 * about the spoken conversation itself: there is no socket, no microphone and no recogniser
 * here. The realtime path's own coverage is `VoiceAgentViewModelTest` plus the backend's turn
 * tests, and the end-to-end claim still needs a device (`VOICE_DEVICE_ACCEPTANCE_VA11.md`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceInterviewHandoffTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeSession : VoiceInterviewSession {
        var recording = false
        var submitted = 0
        var released = 0

        override val isRecording: Boolean get() = recording
        override var onInterrupted: ((reason: String) -> Unit)? = null
        override suspend fun playQuestionAudio(interviewId: Int, questionId: Int) = Unit
        override fun startRecording(questionId: Int) {
            recording = true
        }

        override suspend fun stopAndSubmitRecording(
            interviewId: Int,
            questionId: Int,
        ): InterviewAnswerDto {
            recording = false
            submitted++
            return InterviewAnswerDto(interviewQuestionId = questionId, answerText = "spoken")
        }

        override fun cancelRecording() {
            recording = false
        }

        override fun onLeftForeground() {
            recording = false
        }

        override fun release() {
            released++
        }
    }

    private fun question(id: Int, order: Int = 1) = InterviewQuestionDto(
        id = id,
        orderIndex = order,
        questionText = "Question " + id,
        basedOn = "DSA",
    )

    /** Counts the reads, because "did it ask the server again?" is the whole point here. */
    private class Repo(private val serve: (Int) -> NextInterviewQuestionResponseDto) {
        var reads = 0
        val fake = FakeInterviewRepository()

        init {
            fake.startResult = { type, mode, jdId ->
                InterviewDto(id = 7, type = type, mode = mode, jdId = jdId, status = "in_progress")
            }
            fake.nextQuestionResult = {
                reads++
                serve(reads)
            }
        }
    }

    /** Serves the given ids in order, then completion -- so a re-read is visible by id. */
    private fun sequence(vararg ids: Int) = Repo { read ->
        val id = ids.getOrNull(read - 1)
        if (id == null) {
            NextInterviewQuestionResponseDto(completed = true, question = null)
        } else {
            NextInterviewQuestionResponseDto(completed = false, question = question(id, read))
        }
    }

    private fun viewModel(
        session: VoiceInterviewSession,
        repo: Repo,
        handle: SavedStateHandle = SavedStateHandle(),
    ) = VoiceInterviewViewModel(
        jdId = 0,
        type = "technical",
        manager = session,
        interviewRepository = repo.fake,
        savedStateHandle = handle,
    )

    private fun turn(state: VoiceInterviewUiState) = state as VoiceInterviewUiState.Turn

    // --------------------------------------------------------- which ids leave the screen

    @Test
    fun `the turn carries the interview id the server returned, not a remembered one`() =
        runTest(dispatcher) {
            val repo = sequence(11)
            val vm = viewModel(FakeSession(), repo)
            testScheduler.advanceUntilIdle()

            val current = turn(vm.uiState.value)
            // 7 is the id the fake's startInterview returned. The pair the gateway validates is
            // (interview, question); a screen that paired a question with anything else would
            // have its socket refused.
            assertEquals(7, current.interviewId)
            assertEquals(11, current.question.id)
        }

    @Test
    fun `a resumed interview still pairs the question with its own interview`() =
        runTest(dispatcher) {
            // No startInterview call at all on this path -- the id comes from saved state, and
            // this is the case where a stale pairing would be easiest to introduce.
            val repo = sequence(31)
            val vm = viewModel(FakeSession(), repo, SavedStateHandle(mapOf("interviewId" to 7)))
            testScheduler.advanceUntilIdle()

            assertEquals(7, turn(vm.uiState.value).interviewId)
            assertEquals(31, turn(vm.uiState.value).question.id)
        }

    @Test
    fun `handing off submits nothing and advances nothing`() = runTest(dispatcher) {
        val session = FakeSession()
        val repo = sequence(11, 12)
        val vm = viewModel(session, repo)
        testScheduler.advanceUntilIdle()
        val before = repo.reads

        vm.onHandedOffToLiveConversation()
        testScheduler.advanceUntilIdle()

        // The agent owns the submission. A client that also submitted would be the duplicate
        // answer the interview engine's idempotency exists to catch -- better not to send one.
        assertEquals(0, session.submitted)
        assertEquals(before, repo.reads)
        assertEquals(11, turn(vm.uiState.value).question.id)
    }

    // ------------------------------------------------------------------- coming back

    @Test
    fun `returning after a hand-off re-reads the authoritative question`() = runTest(dispatcher) {
        val repo = sequence(11, 12)
        val vm = viewModel(FakeSession(), repo)
        testScheduler.advanceUntilIdle()
        assertEquals(11, turn(vm.uiState.value).question.id)

        vm.onHandedOffToLiveConversation()
        vm.onReturnedToForeground()
        testScheduler.advanceUntilIdle()

        // The conversation answered 11. The screen does not know that and does not guess: it
        // asks, and renders what came back.
        assertEquals(12, turn(vm.uiState.value).question.id)
        assertEquals(2, repo.reads)
    }

    @Test
    fun `returning without a hand-off re-reads nothing`() = runTest(dispatcher) {
        val repo = sequence(11, 12)
        val vm = viewModel(FakeSession(), repo)
        testScheduler.advanceUntilIdle()

        // An ordinary resume: a notification shade, a permission dialog. Re-reading here would
        // replay the question audio every time the student glanced at something else.
        vm.onReturnedToForeground()
        vm.onReturnedToForeground()
        testScheduler.advanceUntilIdle()

        assertEquals(1, repo.reads)
        assertEquals(11, turn(vm.uiState.value).question.id)
    }

    @Test
    fun `a hand-off is consumed once, not on every later resume`() = runTest(dispatcher) {
        val repo = sequence(11, 12, 13)
        val vm = viewModel(FakeSession(), repo)
        testScheduler.advanceUntilIdle()

        vm.onHandedOffToLiveConversation()
        vm.onReturnedToForeground()
        testScheduler.advanceUntilIdle()
        vm.onReturnedToForeground()
        vm.onReturnedToForeground()
        testScheduler.advanceUntilIdle()

        assertEquals(2, repo.reads)
        assertEquals(12, turn(vm.uiState.value).question.id)
    }

    @Test
    fun `an interview the conversation finished comes back completed, not as a question`() =
        runTest(dispatcher) {
            val repo = sequence(11)
            val session = FakeSession()
            val vm = viewModel(session, repo)
            testScheduler.advanceUntilIdle()

            vm.onHandedOffToLiveConversation()
            vm.onReturnedToForeground()
            testScheduler.advanceUntilIdle()

            // The server says there is nothing pending. The existing completion path runs --
            // including releasing the session -- rather than a second "are we done?" rule.
            assertEquals(VoiceInterviewUiState.Completed(7), vm.uiState.value)
            assertTrue(session.released > 0)
        }

    @Test
    fun `an interview that became inaccessible reports an error rather than a stale question`() =
        runTest(dispatcher) {
            val repo = Repo { throw IllegalStateException("interview not found") }
            val vm = viewModel(FakeSession(), repo, SavedStateHandle(mapOf("interviewId" to 7)))
            testScheduler.advanceUntilIdle()

            assertTrue(vm.uiState.value is VoiceInterviewUiState.Error)
        }

    @Test
    fun `a read that fails after a hand-off does not leave the old question on screen`() =
        runTest(dispatcher) {
            var fail = false
            val repo = Repo { read ->
                if (fail) throw IllegalStateException("gone")
                NextInterviewQuestionResponseDto(completed = false, question = question(11, read))
            }
            val vm = viewModel(FakeSession(), repo)
            testScheduler.advanceUntilIdle()

            fail = true
            vm.onHandedOffToLiveConversation()
            vm.onReturnedToForeground()
            testScheduler.advanceUntilIdle()

            // Showing question 11 again would invite a second answer to it. An error says the
            // true thing: we do not currently know where this interview stands.
            assertTrue(vm.uiState.value is VoiceInterviewUiState.Error)
        }

    // -------------------------------------------------------------------- process death

    @Test
    fun `a process killed during the conversation resyncs exactly once`() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("interviewId" to 7))
        val first = sequence(11, 12)
        val vm = viewModel(FakeSession(), first, handle)
        testScheduler.advanceUntilIdle()
        vm.onHandedOffToLiveConversation()

        // A long spoken conversation is exactly when this process gets killed. The same
        // SavedStateHandle comes back to a new instance, which re-reads from `init` -- and that
        // read is already the resync.
        val second = sequence(12, 13)
        val restored = viewModel(FakeSession(), second, handle)
        testScheduler.advanceUntilIdle()
        assertEquals(1, second.reads)
        assertEquals(12, turn(restored.uiState.value).question.id)

        // The resume that follows the restore must not read again. An earlier version of this
        // persisted the hand-off flag, so it did -- and played the question audio twice.
        restored.onReturnedToForeground()
        testScheduler.advanceUntilIdle()

        assertEquals(1, second.reads)
        assertEquals(12, turn(restored.uiState.value).question.id)
    }

    @Test
    fun `the hand-off does not leak into a new interview on the same screen`() =
        runTest(dispatcher) {
            // No saved interview id: this instance creates its own. A flag carried in from
            // somewhere else would make the first question of a brand-new interview re-read
            // immediately on the first resume.
            val repo = sequence(11, 12)
            val vm = viewModel(FakeSession(), repo)
            testScheduler.advanceUntilIdle()

            vm.onReturnedToForeground()
            testScheduler.advanceUntilIdle()

            assertEquals(1, repo.reads)
            assertEquals(11, turn(vm.uiState.value).question.id)
        }

    @Test
    fun `a hand-off is not recorded when there is no question to hand off`() =
        runTest(dispatcher) {
            val repo = Repo { NextInterviewQuestionResponseDto(completed = true, question = null) }
            val vm = viewModel(FakeSession(), repo)
            testScheduler.advanceUntilIdle()
            assertTrue(vm.uiState.value is VoiceInterviewUiState.Completed)

            vm.onHandedOffToLiveConversation()
            vm.onReturnedToForeground()
            testScheduler.advanceUntilIdle()

            // A completed interview has no pending question, so there is nothing to resync to
            // and `loadNextTurn` must not be re-entered on the next resume.
            assertEquals(1, repo.reads)
        }
}
