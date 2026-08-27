package com.aiplacementos.app.ui.careerpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.InterviewPrepCategorySummaryDto
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CareerPathInterviewPrepViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load surfaces the path's interview prep summaries`() {
        val summaries = listOf(InterviewPrepCategorySummaryDto("technical", 5, 80, emptyList()))
        val repository = FakeCareerPathRepository().apply { pathResult = { sampleCareerPath(interviewPrep = summaries) } }
        val viewModel = CareerPathInterviewPrepViewModel(1, repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<*>)
        assertEquals(summaries, (state as UiState.Success).data.interviewPrep)
    }

    @Test
    fun `load surfaces an error message on failure`() {
        val repository = FakeCareerPathRepository().apply { pathResult = { throw java.io.IOException("down") } }
        val viewModel = CareerPathInterviewPrepViewModel(1, repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }
}
