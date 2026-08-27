package com.aiplacementos.app.ui.atscheck

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.AtsCheckReportDto
import com.aiplacementos.app.fakes.FakeAtsCheckRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AtsCheckParsedViewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load surfaces the report, including raw text`() {
        val report = sampleReport()
        val repository = FakeAtsCheckRepository().apply { getResult = Result.success(report) }
        val viewModel = AtsCheckParsedViewViewModel(1, repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<AtsCheckReportDto>)
        assertEquals(report.rawText, (state as UiState.Success<AtsCheckReportDto>).data.rawText)
    }

    @Test
    fun `load surfaces an error message on failure`() {
        val repository = FakeAtsCheckRepository().apply { getResult = Result.failure(java.io.IOException("down")) }
        val viewModel = AtsCheckParsedViewViewModel(1, repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }
}
