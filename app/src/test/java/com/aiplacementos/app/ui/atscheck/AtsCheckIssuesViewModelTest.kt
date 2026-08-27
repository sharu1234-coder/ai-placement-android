package com.aiplacementos.app.ui.atscheck

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.AtsIssueDto
import com.aiplacementos.app.fakes.FakeAtsCheckRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AtsCheckIssuesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load surfaces the report's issues`() {
        val repository = FakeAtsCheckRepository().apply { getResult = Result.success(sampleReport()) }
        val viewModel = AtsCheckIssuesViewModel(1, repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<List<AtsIssueDto>>)
        assertEquals(1, (state as UiState.Success<List<AtsIssueDto>>).data.size)
    }

    @Test
    fun `load surfaces Empty when the report has no issues`() {
        val repository = FakeAtsCheckRepository().apply {
            getResult = Result.success(sampleReport().copy(issues = emptyList()))
        }
        val viewModel = AtsCheckIssuesViewModel(1, repository)

        assertTrue(viewModel.uiState.value is UiState.Empty)
    }
}
