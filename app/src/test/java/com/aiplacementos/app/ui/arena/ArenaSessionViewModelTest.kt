package com.aiplacementos.app.ui.arena

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ArenaAttemptResultDto
import com.aiplacementos.app.fakes.FakeArenaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun sampleResult() = ArenaAttemptResultDto(
    arenaAttemptId = 1, score = 0.8, timedOut = false, isPersonalBest = true, previousBestScore = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ArenaSessionViewModelTest {
    private val scheduler = TestCoroutineScheduler()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher(scheduler))

    @Test
    fun `countdown decrements as time passes`() {
        val repository = FakeArenaRepository()
        val viewModel = ArenaSessionViewModel(1, 10, repository)

        scheduler.advanceTimeBy(3_000)
        scheduler.runCurrent()

        assertEquals(7, viewModel.remainingSeconds.value)
        assertTrue(viewModel.uiState.value is ArenaSessionUiState.InProgress)
    }

    @Test
    fun `timer running out automatically completes the attempt with the full time limit`() {
        val repository = FakeArenaRepository().apply { completeAttemptResult = { _, _ -> sampleResult() } }
        val viewModel = ArenaSessionViewModel(1, 5, repository)

        scheduler.advanceTimeBy(5_000)
        scheduler.runCurrent()

        assertTrue(viewModel.uiState.value is ArenaSessionUiState.Finished)
        assertEquals(5, repository.lastElapsed)
    }

    @Test
    fun `finishing questions early stops the timer and completes with the elapsed time so far`() {
        val repository = FakeArenaRepository().apply { completeAttemptResult = { _, _ -> sampleResult() } }
        val viewModel = ArenaSessionViewModel(1, 60, repository)

        scheduler.advanceTimeBy(10_000)
        scheduler.runCurrent()
        viewModel.onQuestionsFinished()

        assertEquals(10, repository.lastElapsed)
        assertTrue(viewModel.uiState.value is ArenaSessionUiState.Finished)
    }

    @Test
    fun `calling onQuestionsFinished twice only completes once`() {
        val repository = FakeArenaRepository().apply { completeAttemptResult = { _, _ -> sampleResult() } }
        val viewModel = ArenaSessionViewModel(1, 60, repository)

        viewModel.onQuestionsFinished()
        viewModel.onQuestionsFinished()

        assertEquals(0, repository.lastElapsed)
        assertTrue(viewModel.uiState.value is ArenaSessionUiState.Finished)
    }

    @Test
    fun `a completion failure surfaces an error and retry recovers`() {
        var callCount = 0
        val repository = FakeArenaRepository().apply {
            completeAttemptResult = { _, _ ->
                callCount += 1
                if (callCount == 1) throw java.net.UnknownHostException("no route") else sampleResult()
            }
        }
        val viewModel = ArenaSessionViewModel(1, 60, repository)

        viewModel.onQuestionsFinished()
        val failed = viewModel.uiState.value
        assertTrue(failed is ArenaSessionUiState.Completing && failed.error != null)

        viewModel.retryCompletion()
        assertTrue(viewModel.uiState.value is ArenaSessionUiState.Finished)
    }
}
