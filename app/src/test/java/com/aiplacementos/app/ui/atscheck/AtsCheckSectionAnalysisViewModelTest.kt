package com.aiplacementos.app.ui.atscheck

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.SectionAnalysisDto
import com.aiplacementos.app.fakes.FakeAtsCheckRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AtsCheckSectionAnalysisViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load surfaces the report's section analysis`() {
        val repository = FakeAtsCheckRepository().apply { getResult = Result.success(sampleReport()) }
        val viewModel = AtsCheckSectionAnalysisViewModel(1, repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<List<SectionAnalysisDto>>)
        assertEquals("contact", (state as UiState.Success<List<SectionAnalysisDto>>).data.first().section)
    }

    @Test
    fun `every section maps to a real, non-blank category destination`() {
        val sections = listOf(
            "contact", "education", "skills", "certifications", "languages",
            "summary", "experience", "projects", "achievements",
        )
        val validCategories = setOf("parsing", "structure", "content", "skills", "experience", "projects", "contact", "readability")

        sections.forEach { section ->
            assertTrue("category for '$section' must be a real category", categoryForSection(section) in validCategories)
        }
    }
}
