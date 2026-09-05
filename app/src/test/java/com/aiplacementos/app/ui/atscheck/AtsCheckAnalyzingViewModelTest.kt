package com.aiplacementos.app.ui.atscheck

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.AtsCheckReportDto
import com.aiplacementos.app.fakes.FakeAtsCheckRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AtsCheckAnalyzingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @Test
    fun `advances through stages on a timer while the network call is still pending`() = runTest(testDispatcher) {
        val repository = FakeAtsCheckRepository().apply {
            createDelayMs = 10_000L // outlives the whole stage-advancing loop (7 * 900ms = 6300ms)
            createResult = Result.success(sampleReport())
        }
        val viewModel = AtsCheckAnalyzingViewModel(resumeId = 1, targetRole = null, atsCheckRepository = repository)

        viewModel.start()
        advanceTimeBy(950) // just past the first stage's delay

        val state = viewModel.uiState.value
        assertTrue(state is AtsCheckAnalyzingUiState.InProgress)
        assertEquals(1, (state as AtsCheckAnalyzingUiState.InProgress).stageIndex)
    }

    @Test
    fun `stays on the last stage until the real network response lands, then completes`() = runTest(testDispatcher) {
        val report = sampleReport(id = 77)
        val repository = FakeAtsCheckRepository().apply {
            createDelayMs = 10_000L
            createResult = Result.success(report)
        }
        val viewModel = AtsCheckAnalyzingViewModel(resumeId = 1, targetRole = null, atsCheckRepository = repository)

        viewModel.start()
        advanceTimeBy(6_400) // past all 6 timed stages -- lands on the final, network-gated stage

        assertEquals(
            AtsCheckAnalyzingUiState.InProgress(AtsCheckStageLabels.size - 1),
            viewModel.uiState.value,
        )

        advanceUntilIdle() // let the remaining network delay elapse
        assertEquals(AtsCheckAnalyzingUiState.Completed(77), viewModel.uiState.value)
    }

    @Test
    fun `a fast network response short-circuits the remaining stage animation`() = runTest(testDispatcher) {
        val repository = FakeAtsCheckRepository().apply {
            createDelayMs = 0 // resolves as soon as it's scheduled
            createResult = Result.success(sampleReport(id = 5))
        }
        val viewModel = AtsCheckAnalyzingViewModel(resumeId = 1, targetRole = null, atsCheckRepository = repository)

        viewModel.start()
        advanceUntilIdle()

        assertEquals(AtsCheckAnalyzingUiState.Completed(5), viewModel.uiState.value)
    }

    @Test
    fun `a failed check surfaces an error instead of completing`() = runTest(testDispatcher) {
        val repository = FakeAtsCheckRepository().apply {
            createDelayMs = 0
            createResult = Result.failure(java.io.IOException("no network"))
        }
        val viewModel = AtsCheckAnalyzingViewModel(resumeId = 1, targetRole = null, atsCheckRepository = repository)

        viewModel.start()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AtsCheckAnalyzingUiState.Error)
    }

    @Test
    fun `target role is threaded through to the repository call`() = runTest(testDispatcher) {
        val repository = FakeAtsCheckRepository().apply {
            createDelayMs = 0
            createResult = Result.success(sampleReport())
        }
        val viewModel = AtsCheckAnalyzingViewModel(resumeId = 9, targetRole = "Backend Engineer", atsCheckRepository = repository)

        viewModel.start()
        advanceUntilIdle()

        assertEquals(9, repository.lastCreateResumeId)
        assertEquals("Backend Engineer", repository.lastCreateTargetRole)
    }

    /** Regression: the screen kicks start() off from a LaunchedEffect(Unit), which re-runs on
     * the fresh composition a rotation produces while this ViewModel survives it. Before the
     * in-flight guard that fired a second POST /ats-check -- a second billable server-side AI
     * analysis and a duplicate report row for one user action. */
    @Test
    fun `a second start while one is already in flight does not create a second analysis`() = runTest(testDispatcher) {
        val repository = FakeAtsCheckRepository().apply {
            createDelayMs = 10_000L
            createResult = Result.success(sampleReport(id = 42))
        }
        val viewModel = AtsCheckAnalyzingViewModel(resumeId = 1, targetRole = null, atsCheckRepository = repository)

        viewModel.start()
        advanceTimeBy(950)
        viewModel.start() // e.g. the LaunchedEffect re-running after a rotation
        viewModel.start() // e.g. an impatient second tap on "Try Again"
        advanceUntilIdle()

        assertEquals(1, repository.createCallCount)
        assertEquals(AtsCheckAnalyzingUiState.Completed(42), viewModel.uiState.value)
    }

    /** Once an analysis has completed the screen is navigating away; a late re-entry of the
     * effect must not silently start a whole second analysis behind that navigation. */
    @Test
    fun `start after completion is a no-op`() = runTest(testDispatcher) {
        val repository = FakeAtsCheckRepository().apply {
            createDelayMs = 0
            createResult = Result.success(sampleReport(id = 3))
        }
        val viewModel = AtsCheckAnalyzingViewModel(resumeId = 1, targetRole = null, atsCheckRepository = repository)

        viewModel.start()
        advanceUntilIdle()
        viewModel.start()
        advanceUntilIdle()

        assertEquals(1, repository.createCallCount)
        assertEquals(AtsCheckAnalyzingUiState.Completed(3), viewModel.uiState.value)
    }

    /** Retry after a real failure must still work -- the guard is "one in flight", not "one ever". */
    @Test
    fun `retry after a failure starts a fresh analysis`() = runTest(testDispatcher) {
        val repository = FakeAtsCheckRepository().apply {
            createDelayMs = 0
            createResult = Result.failure(java.io.IOException("no network"))
        }
        val viewModel = AtsCheckAnalyzingViewModel(resumeId = 1, targetRole = null, atsCheckRepository = repository)

        viewModel.start()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AtsCheckAnalyzingUiState.Error)

        repository.createResult = Result.success(sampleReport(id = 8))
        viewModel.start()
        advanceUntilIdle()

        assertEquals(2, repository.createCallCount)
        assertEquals(AtsCheckAnalyzingUiState.Completed(8), viewModel.uiState.value)
    }
}

private fun sampleReport(id: Int = 1) = AtsCheckReportDto(
    id = id,
    resumeId = 1,
    targetRole = null,
    overallScore = 80,
    scoreCategory = "very_good",
    categoryBreakdown = emptyList(),
    sectionAnalysis = emptyList(),
    issues = emptyList(),
    recommendations = emptyList(),
    potentialScore = null,
    potentialScoreExplanation = null,
    aiSummary = "A solid resume.",
    rawText = "raw text",
    createdAt = "2026-01-01T00:00:00Z",
)
