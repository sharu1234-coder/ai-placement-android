package com.aiplacementos.app.ui.atscheck

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.AtsCheckListItemDto
import com.aiplacementos.app.fakes.FakeAtsCheckRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AtsCheckHistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load surfaces the check history most-recent-first as returned by the repository`() {
        val checks = listOf(
            AtsCheckListItemDto(id = 2, resumeId = 1, resumeFilename = "v2.pdf", overallScore = 82, scoreCategory = "very_good", createdAt = "2026-08-26T10:30:00Z"),
            AtsCheckListItemDto(id = 1, resumeId = 1, resumeFilename = "v1.pdf", overallScore = 64, scoreCategory = "needs_improvement", createdAt = "2026-08-10T11:20:00Z"),
        )
        val repository = FakeAtsCheckRepository().apply { listResult = Result.success(checks) }
        val viewModel = AtsCheckHistoryViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<List<AtsCheckListItemDto>>)
        assertEquals(checks, (state as UiState.Success<List<AtsCheckListItemDto>>).data)
    }

    @Test
    fun `load surfaces Empty -- not Error -- when no checks exist yet`() {
        val repository = FakeAtsCheckRepository().apply { listResult = Result.success(emptyList()) }
        val viewModel = AtsCheckHistoryViewModel(repository)

        assertTrue(viewModel.uiState.value is UiState.Empty)
    }

    @Test
    fun `load surfaces an error message on failure`() {
        val repository = FakeAtsCheckRepository().apply { listResult = Result.failure(java.io.IOException("down")) }
        val viewModel = AtsCheckHistoryViewModel(repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }
}
