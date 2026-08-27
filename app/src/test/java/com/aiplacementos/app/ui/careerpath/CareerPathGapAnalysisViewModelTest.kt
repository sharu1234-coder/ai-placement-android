package com.aiplacementos.app.ui.careerpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.SkillGapItemDto
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CareerPathGapAnalysisViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load surfaces the path's skill gaps`() {
        val gaps = listOf(
            SkillGapItemDto("SQL", "strong", "Present on your resume."),
            SkillGapItemDto("Power BI", "missing", "Not yet on your resume."),
        )
        val repository = FakeCareerPathRepository().apply { pathResult = { sampleCareerPath(skillGaps = gaps) } }
        val viewModel = CareerPathGapAnalysisViewModel(1, repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<*>)
        assertEquals(2, (state as UiState.Success).data.skillGaps.size)
    }

    @Test
    fun `load surfaces an error message on failure`() {
        val repository = FakeCareerPathRepository().apply { pathResult = { throw java.io.IOException("down") } }
        val viewModel = CareerPathGapAnalysisViewModel(1, repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }
}
