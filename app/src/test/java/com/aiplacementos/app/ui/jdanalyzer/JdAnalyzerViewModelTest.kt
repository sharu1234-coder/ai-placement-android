package com.aiplacementos.app.ui.jdanalyzer

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.JobDescriptionListItemDto
import com.aiplacementos.app.data.remote.JobDescriptionParseResponseDto
import com.aiplacementos.app.data.remote.JobPreparationPlanDto
import com.aiplacementos.app.data.remote.JobSummaryDto
import com.aiplacementos.app.fakes.FakeJobRepository
import com.aiplacementos.app.fakes.httpException
import com.aiplacementos.app.ui.common.UiState
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JdAnalyzerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loadPastAnalyses populates the list`() {
        val analyses = listOf(testListItem(id = 1), testListItem(id = 2))
        val repository = FakeJobRepository().apply { listResult = { analyses } }
        val viewModel = JdAnalyzerViewModel(repository)

        viewModel.loadPastAnalyses()

        assertEquals(UiState.Success(analyses), viewModel.pastAnalyses.value)
    }

    @Test
    fun `loadPastAnalyses surfaces Empty for an empty list`() {
        val repository = FakeJobRepository().apply { listResult = { emptyList() } }
        val viewModel = JdAnalyzerViewModel(repository)

        viewModel.loadPastAnalyses()

        assertEquals(UiState.Empty, viewModel.pastAnalyses.value)
    }

    @Test
    fun `loadPastAnalyses surfaces a connectivity message on failure`() {
        val repository = FakeJobRepository().apply { listResult = { throw IOException("no network") } }
        val viewModel = JdAnalyzerViewModel(repository)

        viewModel.loadPastAnalyses()

        val state = viewModel.pastAnalyses.value
        assertTrue(state is UiState.Error)
    }

    @Test
    fun `analyze parses then generates a plan and invokes onAnalyzed with the new jd id`() {
        val parsed = testParseResponse(id = 42)
        val plan = testPlan(jdId = 42)
        val repository = FakeJobRepository().apply {
            parseResult = { parsed }
            generatePlanResult = { _, _, _ -> plan }
            listResult = { emptyList() }
        }
        val viewModel = JdAnalyzerViewModel(repository)

        var analyzedJdId: Int? = null
        viewModel.analyze("some JD text", "Acme Corp", "Backend Engineer", onAnalyzed = { analyzedJdId = it })

        assertEquals(42, analyzedJdId)
        assertEquals("some JD text", repository.lastParsedText)
        assertEquals(Triple(42, "Acme Corp", "Backend Engineer"), repository.lastGeneratePlanArgs)
        assertNull(viewModel.analyzeError.value)
        assertTrue(!viewModel.analyzing.value)
    }

    @Test
    fun `analyze blanks out optional company and title before sending them`() {
        val parsed = testParseResponse(id = 1)
        val repository = FakeJobRepository().apply {
            parseResult = { parsed }
            generatePlanResult = { _, _, _ -> testPlan(jdId = 1) }
        }
        val viewModel = JdAnalyzerViewModel(repository)

        viewModel.analyze("jd text", "  ", "  ", onAnalyzed = {})

        assertEquals(Triple(1, null, null), repository.lastGeneratePlanArgs)
    }

    @Test
    fun `analyze surfaces a resume-required message on 422`() {
        val repository = FakeJobRepository().apply {
            parseResult = { testParseResponse(id = 1) }
            generatePlanResult = { _, _, _ -> throw httpException(422) }
        }
        val viewModel = JdAnalyzerViewModel(repository)

        var called = false
        viewModel.analyze("jd text", "", "", onAnalyzed = { called = true })

        assertEquals(false, called)
        assertEquals(
            "Upload and complete your resume first, then analyze this job description.",
            viewModel.analyzeError.value,
        )
    }

    @Test
    fun `analyze surfaces a connectivity message on network failure`() {
        val repository = FakeJobRepository().apply {
            parseResult = { throw IOException("no network") }
        }
        val viewModel = JdAnalyzerViewModel(repository)

        viewModel.analyze("jd text", "", "", onAnalyzed = {})

        assertEquals(
            "Couldn't analyze that job description. Check your connection and try again.",
            viewModel.analyzeError.value,
        )
    }

    @Test
    fun `deleteAnalysis removes the item and reloads the list`() {
        var listCallCount = 0
        val repository = FakeJobRepository().apply {
            listResult = { listCallCount++; emptyList() }
            deleteResult = { }
        }
        val viewModel = JdAnalyzerViewModel(repository)

        viewModel.deleteAnalysis(7)

        assertEquals(7, repository.lastDeletedJdId)
        assertEquals(1, listCallCount)
    }
}

private fun testListItem(id: Int) = JobDescriptionListItemDto(
    id = id,
    roleTitle = "Backend Engineer",
    location = "Bengaluru",
    createdAt = "2026-01-01T00:00:00Z",
    hasPreparationPlan = true,
    preparationPlanCompanyName = "Acme Corp",
    preparationPlanJobTitle = "Backend Engineer",
)

private fun testParseResponse(id: Int) = JobDescriptionParseResponseDto(
    id = id,
    roleTitle = "Backend Engineer",
    detectedRoleId = null,
    parsingConfidence = 0.9,
    requirements = emptyList(),
)

private fun testPlan(jdId: Int) = JobPreparationPlanDto(
    id = 1,
    jdId = jdId,
    companyName = "Acme Corp",
    jobTitle = "Backend Engineer",
    jobSummary = JobSummaryDto(
        companyName = "Acme Corp",
        jobTitle = "Backend Engineer",
        experienceExpectation = null,
        educationEligibility = null,
        location = null,
        employmentType = null,
    ),
    requiredSkills = emptyList(),
    preparationGaps = emptyList(),
    preparationPlan = emptyList(),
    interviewPrep = emptyList(),
    createdAt = "2026-01-01T00:00:00Z",
)
