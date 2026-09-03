package com.aiplacementos.app.ui.interview

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.WeaknessRecordDto
import com.aiplacementos.app.fakes.FakeWeaknessRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.UnknownHostException

private fun sampleRecord(
    skillKey: String = "interview:technical_correctness",
    status: String = "REPEATED",
    occurrences: Int = 2,
    consecutiveGoodSignals: Int = 0,
    lastScore: Double = 0.3,
) = WeaknessRecordDto(
    skillKey = skillKey,
    status = status,
    occurrences = occurrences,
    consecutiveGoodSignals = consecutiveGoodSignals,
    lastScore = lastScore,
    lastSeenAt = "2026-09-02T10:00:00Z",
)

class InterviewMemoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows Success with the returned records`() {
        val repository = FakeWeaknessRepository().apply { result = { listOf(sampleRecord()) } }
        val viewModel = InterviewMemoryViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is InterviewMemoryUiState.Success)
        assertEquals(1, (state as InterviewMemoryUiState.Success).records.size)
    }

    @Test
    fun `empty history is a real empty Success, not an error`() {
        val repository = FakeWeaknessRepository().apply { result = { emptyList() } }
        val viewModel = InterviewMemoryViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is InterviewMemoryUiState.Success)
        assertTrue((state as InterviewMemoryUiState.Success).records.isEmpty())
    }

    @Test
    fun `a failed fetch degrades to an empty Success rather than showing an error`() {
        val repository = FakeWeaknessRepository().apply { result = { throw UnknownHostException("no route") } }
        val viewModel = InterviewMemoryViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is InterviewMemoryUiState.Success)
        assertTrue((state as InterviewMemoryUiState.Success).records.isEmpty())
    }
}
