package com.aiplacementos.app.ui.tests

import androidx.lifecycle.SavedStateHandle
import com.aiplacementos.app.data.remote.AssessmentDto
import com.aiplacementos.app.data.remote.AssessmentHistoryItemDto
import com.aiplacementos.app.data.remote.AssessmentResultDto
import com.aiplacementos.app.data.remote.AnswerResultDto
import com.aiplacementos.app.data.remote.AssessmentBlueprintDto
import com.aiplacementos.app.data.remote.NextQuestionResponseDto
import com.aiplacementos.app.domain.repository.AssessmentRepository
import com.aiplacementos.app.ui.common.UiState
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * One student, one test: how many assessments the server is asked to create.
 *
 * POST /assessments inserts unconditionally, and this screen creates the assessment on open --
 * before the student has pressed Begin -- so anything that rebuilds the view model without a new
 * intent is a duplicate row. The assertion throughout is the request count, not the rendered
 * state, because both duplicates and the single correct case look identical on screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class CountingRepository(
        private val failWith: Throwable? = null,
    ) : AssessmentRepository {
        val starts = AtomicInteger(0)

        /** Every key the screen sent, in order -- the stability of these is the assertion. */
        val keys = mutableListOf<String?>()

        override suspend fun startAssessment(blueprintId: Int, idempotencyKey: String?): AssessmentDto {
            val n = starts.incrementAndGet()
            keys += idempotencyKey
            failWith?.let { throw it }
            return AssessmentDto(id = 100 + n, blueprintId = blueprintId, status = "in_progress")
        }

        override suspend fun createBlueprint(
        roleId: Int?,
        jdId: Int?,
        category: String?,
        idempotencyKey: String?,
    ): AssessmentBlueprintDto =
            throw UnsupportedOperationException()

        override suspend fun nextQuestion(assessmentId: Int): NextQuestionResponseDto =
            throw UnsupportedOperationException()

        override suspend fun submitAnswer(
            assessmentId: Int,
            questionId: Int,
            studentAnswer: String,
            timeTakenSeconds: Int?,
        ): AnswerResultDto = throw UnsupportedOperationException()

        override suspend fun getResult(assessmentId: Int): AssessmentResultDto =
            throw UnsupportedOperationException()

        override suspend fun getHistory(): List<AssessmentHistoryItemDto> = emptyList()
    }

    private fun viewModel(repo: AssessmentRepository, state: SavedStateHandle) =
        TestListViewModel(blueprintId = 7, assessmentRepository = repo, savedStateHandle = state)

    @Test
    fun `opening the screen starts one assessment`() = runTest(dispatcher) {
        val repo = CountingRepository()

        val vm = viewModel(repo, SavedStateHandle())
        advanceUntilIdle()

        assertEquals(1, repo.starts.get())
        assertEquals(UiState.Success(AssessmentDto(101, 7, "in_progress")), vm.uiState.value)
    }

    @Test
    fun `process death does not start a second assessment`() = runTest(dispatcher) {
        val repo = CountingRepository()
        // The handle is what survives: the process is gone, the view model with it.
        val survives = SavedStateHandle()
        viewModel(repo, survives)
        advanceUntilIdle()
        assertEquals(1, repo.starts.get())

        val recreated = viewModel(repo, survives)
        advanceUntilIdle()

        assertEquals("the assessment already created is reused", 1, repo.starts.get())
        assertTrue(recreated.uiState.value is UiState.Success)
        assertEquals(101, (recreated.uiState.value as UiState.Success).data.id)
    }

    @Test
    fun `a second start while one is in flight joins it`() = runTest(dispatcher) {
        val repo = CountingRepository()

        val vm = viewModel(repo, SavedStateHandle())
        vm.start()          // the impatient second tap, before the first has answered
        vm.start()
        advanceUntilIdle()

        assertEquals(1, repo.starts.get())
    }

    @Test
    fun `Try Again after the assessment exists does not create another`() = runTest(dispatcher) {
        val repo = CountingRepository()
        val vm = viewModel(repo, SavedStateHandle())
        advanceUntilIdle()

        vm.start()
        advanceUntilIdle()

        assertEquals(1, repo.starts.get())
    }

    @Test
    fun `the start carries an idempotency key`() = runTest(dispatcher) {
        val repo = CountingRepository()

        viewModel(repo, SavedStateHandle())
        advanceUntilIdle()

        assertEquals(1, repo.keys.size)
        val key = repo.keys.single()
        assertTrue("a key was sent: $key", key != null && key.length in 8..64)
    }

    @Test
    fun `a retry after a genuine failure reuses the same key`() = runTest(dispatcher) {
        // The point of holding the key rather than minting one per request. The first attempt
        // may have committed before it failed to answer, so the retry has to be recognisable
        // as the same operation -- a fresh key would be a second one.
        val repo = CountingRepository(failWith = IOException("no connection"))
        val vm = viewModel(repo, SavedStateHandle())
        advanceUntilIdle()

        vm.start()
        advanceUntilIdle()

        assertEquals(2, repo.starts.get())
        assertEquals("both attempts were the same operation", 1, repo.keys.toSet().size)
    }

    @Test
    fun `a recreated screen reuses the key it already minted`() = runTest(dispatcher) {
        // Process death: the id is restored so no request is sent at all, but if one ever were,
        // it must not be a new operation. Asserting on the handle keeps that true even if the
        // id short-circuit above is later removed.
        val survives = SavedStateHandle()
        val repo = CountingRepository()
        viewModel(repo, survives)
        advanceUntilIdle()
        val first = survives.get<String>("startKey")

        viewModel(repo, survives)
        advanceUntilIdle()

        assertNotNull(first)
        assertEquals(first, survives.get<String>("startKey"))
    }

    @Test
    fun `a genuine failure leaves the student able to try again`() = runTest(dispatcher) {
        // Nothing was created, so nothing is being protected -- a retry here is correct and must
        // still work. Guarding too eagerly would strand the student on an error they cannot clear.
        val repo = CountingRepository(failWith = IOException("no connection"))

        val vm = viewModel(repo, SavedStateHandle())
        advanceUntilIdle()
        assertTrue(vm.uiState.value is UiState.Error)

        vm.start()
        advanceUntilIdle()

        assertEquals("an explicit retry after a failure does reach the server", 2, repo.starts.get())
    }
}
