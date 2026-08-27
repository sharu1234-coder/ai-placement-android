package com.aiplacementos.app.ui.atscheck

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.AtsCheckReportDto
import com.aiplacementos.app.fakes.FakeAtsCheckRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AtsCheckResultViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load fetches the report by id`() {
        val report = sampleReport(id = 42)
        val repository = FakeAtsCheckRepository().apply { getResult = Result.success(report) }
        val viewModel = AtsCheckResultViewModel(42, repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<AtsCheckReportDto>)
        assertEquals(report, (state as UiState.Success<AtsCheckReportDto>).data)
        assertEquals(42, repository.lastGetCheckId)
    }

    @Test
    fun `load surfaces an error message on failure`() {
        val repository = FakeAtsCheckRepository().apply { getResult = Result.failure(java.io.IOException("down")) }
        val viewModel = AtsCheckResultViewModel(1, repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }

    @Test
    fun `retry re-fetches the report`() {
        val report = sampleReport(id = 1)
        val repository = FakeAtsCheckRepository().apply { getResult = Result.failure(java.io.IOException("down")) }
        val viewModel = AtsCheckResultViewModel(1, repository)
        assertTrue(viewModel.uiState.value is UiState.Error)

        repository.getResult = Result.success(report)
        viewModel.load()

        assertTrue(viewModel.uiState.value is UiState.Success<*>)
    }
}

internal fun sampleReport(
    id: Int = 1,
    overallScore: Int = 82,
    scoreCategory: String = "very_good",
) = AtsCheckReportDto(
    id = id,
    resumeId = 1,
    targetRole = null,
    overallScore = overallScore,
    scoreCategory = scoreCategory,
    categoryBreakdown = listOf(
        com.aiplacementos.app.data.remote.CategoryBreakdownDto(
            category = "parsing",
            score = 86,
            summary = "Your resume's layout is clean.",
            strengths = listOf("Simple, single-column layout detected"),
            improvements = emptyList(),
            howToImprove = emptyList(),
        ),
    ),
    sectionAnalysis = listOf(
        com.aiplacementos.app.data.remote.SectionAnalysisDto(section = "contact", status = "complete", content = "email@example.com"),
    ),
    issues = listOf(
        com.aiplacementos.app.data.remote.AtsIssueDto(
            category = "content",
            severity = "high",
            title = "Add measurable achievements",
            description = "Most bullets lack measurable impact.",
            howToFix = "Add numbers where truthful.",
        ),
    ),
    recommendations = listOf(
        com.aiplacementos.app.data.remote.AtsRecommendationDto(
            rank = 1,
            category = "content",
            title = "Add measurable achievements",
            priority = "high",
            rationale = "Most bullets lack measurable impact.",
            fixTarget = "resume_upload",
        ),
    ),
    potentialScore = 90,
    potentialScoreExplanation = "Addressing these could raise your score to about 90/100.",
    aiSummary = "Your resume has a strong ATS-friendly foundation.",
    rawText = "G. Sharath Kumar\nemail@example.com",
    createdAt = "2026-08-26T10:30:00Z",
)
