package com.aiplacementos.app.ui.atscheck

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.AtsCheckReportDto
import com.aiplacementos.app.fakes.FakeAtsCheckRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AtsCheckImprovementPlanViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load surfaces the report's recommendations`() {
        val repository = FakeAtsCheckRepository().apply { getResult = Result.success(sampleReport()) }
        val viewModel = AtsCheckImprovementPlanViewModel(1, repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<AtsCheckReportDto>)
        assertEquals(1, (state as UiState.Success<AtsCheckReportDto>).data.recommendations.size)
    }

    @Test
    fun `load surfaces Empty when there are no recommendations`() {
        val repository = FakeAtsCheckRepository().apply {
            getResult = Result.success(sampleReport().copy(recommendations = emptyList()))
        }
        val viewModel = AtsCheckImprovementPlanViewModel(1, repository)

        assertTrue(viewModel.uiState.value is UiState.Empty)
    }
}
