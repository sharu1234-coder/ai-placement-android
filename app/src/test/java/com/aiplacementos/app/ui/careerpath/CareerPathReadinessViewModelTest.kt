package com.aiplacementos.app.ui.careerpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CareerPathReadinessViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load surfaces the path's job readiness`() {
        val repository = FakeCareerPathRepository().apply { pathResult = { sampleCareerPath() } }
        val viewModel = CareerPathReadinessViewModel(1, repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<*>)
        assertEquals(42, (state as UiState.Success).data.jobReadiness.overallPercent)
    }

    @Test
    fun `load surfaces an error message on failure`() {
        val repository = FakeCareerPathRepository().apply { pathResult = { throw java.io.IOException("down") } }
        val viewModel = CareerPathReadinessViewModel(1, repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }
}
