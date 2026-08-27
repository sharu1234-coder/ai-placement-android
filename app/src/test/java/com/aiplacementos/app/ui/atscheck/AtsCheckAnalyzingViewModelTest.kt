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

        viewModel.start(onComplete = {})
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

        var completedId: Int? = null
        viewModel.start(onComplete = { completedId = it })
        advanceTimeBy(6_400) // past all 6 timed stages -- lands on the final, network-gated stage

        assertEquals(
            AtsCheckAnalyzingUiState.InProgress(AtsCheckStageLabels.size - 1),
            viewModel.uiState.value,
        )
        assertEquals(null, completedId) // network still hasn't resolved -- must not have navigated yet

        advanceUntilIdle() // let the remaining network delay elapse
        assertEquals(77, completedId)
    }

    @Test
    fun `a fast network response short-circuits the remaining stage animation`() = runTest(testDispatcher) {
        val repository = FakeAtsCheckRepository().apply {
            createDelayMs = 0 // resolves as soon as it's scheduled
            createResult = Result.success(sampleReport(id = 5))
        }
        val viewModel = AtsCheckAnalyzingViewModel(resumeId = 1, targetRole = null, atsCheckRepository = repository)

        var completedId: Int? = null
        viewModel.start(onComplete = { completedId = it })
        advanceUntilIdle()

        assertEquals(5, completedId)
    }

    @Test
    fun `a failed check surfaces an error instead of navigating`() = runTest(testDispatcher) {
        val repository = FakeAtsCheckRepository().apply {
            createDelayMs = 0
            createResult = Result.failure(java.io.IOException("no network"))
        }
        val viewModel = AtsCheckAnalyzingViewModel(resumeId = 1, targetRole = null, atsCheckRepository = repository)

        var completed = false
        viewModel.start(onComplete = { completed = true })
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AtsCheckAnalyzingUiState.Error)
        assertEquals(false, completed)
    }

    @Test
    fun `target role is threaded through to the repository call`() = runTest(testDispatcher) {
        val repository = FakeAtsCheckRepository().apply {
            createDelayMs = 0
            createResult = Result.success(sampleReport())
        }
        val viewModel = AtsCheckAnalyzingViewModel(resumeId = 9, targetRole = "Backend Engineer", atsCheckRepository = repository)

        viewModel.start(onComplete = {})
        advanceUntilIdle()

        assertEquals(9, repository.lastCreateResumeId)
        assertEquals("Backend Engineer", repository.lastCreateTargetRole)
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
