package com.aiplacementos.app.ui.jdanalyzer

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.JobPreparationPlanDto
import com.aiplacementos.app.data.remote.JobSummaryDto
import com.aiplacementos.app.data.remote.RoadmapDto
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.fakes.FakeJobRepository
import com.aiplacementos.app.fakes.FakeRoadmapRepository
import com.aiplacementos.app.fakes.httpException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JdAnalyzerResultViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load fetches the existing plan`() {
        val plan = testPlan()
        val jobRepository = FakeJobRepository().apply { getPlanResult = { plan } }
        val viewModel = JdAnalyzerResultViewModel(42, jobRepository, FakeRoadmapRepository(), FakeCareerPathRepository())

        val state = viewModel.uiState.value
        assertTrue(state is JdAnalyzerResultUiState.Success)
        assertEquals(plan, (state as JdAnalyzerResultUiState.Success).plan)
        assertEquals(42, jobRepository.lastGetPlanJdId)
    }

    @Test
    fun `load falls back to generating a plan when none exists yet`() {
        val plan = testPlan()
        val jobRepository = FakeJobRepository().apply {
            getPlanResult = { throw httpException(404) }
            generatePlanResult = { _, _, _ -> plan }
        }
        val viewModel = JdAnalyzerResultViewModel(42, jobRepository, FakeRoadmapRepository(), FakeCareerPathRepository())

        val state = viewModel.uiState.value
        assertTrue(state is JdAnalyzerResultUiState.Success)
        assertEquals(Triple(42, null, null), jobRepository.lastGeneratePlanArgs)
    }

    @Test
    fun `load surfaces a resume-required message when generation needs a resume`() {
        val jobRepository = FakeJobRepository().apply {
            getPlanResult = { throw httpException(404) }
            generatePlanResult = { _, _, _ -> throw httpException(422) }
        }
        val viewModel = JdAnalyzerResultViewModel(42, jobRepository, FakeRoadmapRepository(), FakeCareerPathRepository())

        val state = viewModel.uiState.value
        assertTrue(state is JdAnalyzerResultUiState.Error)
        assertEquals(
            "Upload and complete your resume first, then try analyzing this job again.",
            (state as JdAnalyzerResultUiState.Error).message,
        )
    }

    @Test
    fun `load surfaces a connectivity message on network failure`() {
        val jobRepository = FakeJobRepository().apply { getPlanResult = { throw IOException("no network") } }
        val viewModel = JdAnalyzerResultViewModel(42, jobRepository, FakeRoadmapRepository(), FakeCareerPathRepository())

        val state = viewModel.uiState.value
        assertTrue(state is JdAnalyzerResultUiState.Error)
    }

    @Test
    fun `generateRoadmap invokes onReady only after the roadmap is actually generated`() {
        val jobRepository = FakeJobRepository().apply { getPlanResult = { testPlan() } }
        val roadmapRepository = FakeRoadmapRepository().apply {
            generateResult = { _, _ -> testRoadmap() }
        }
        val viewModel = JdAnalyzerResultViewModel(42, jobRepository, roadmapRepository, FakeCareerPathRepository())

        var ready = false
        viewModel.generateRoadmap(14, onReady = { ready = true })

        assertTrue(ready)
        assertEquals(42 to 14, roadmapRepository.lastGenerateArgs)
    }

    @Test
    fun `a generateRoadmap failure reverts the busy state without calling onReady`() {
        val jobRepository = FakeJobRepository().apply { getPlanResult = { testPlan() } }
        val roadmapRepository = FakeRoadmapRepository().apply {
            generateResult = { _, _ -> throw IOException("no network") }
        }
        val viewModel = JdAnalyzerResultViewModel(42, jobRepository, roadmapRepository, FakeCareerPathRepository())

        var ready = false
        viewModel.generateRoadmap(7, onReady = { ready = true })

        assertFalse(ready)
        val state = viewModel.uiState.value
        assertTrue(state is JdAnalyzerResultUiState.Success)
        assertFalse((state as JdAnalyzerResultUiState.Success).isGeneratingRoadmap)
    }

    @Test
    fun `delete calls the repository and invokes onDeleted`() {
        val jobRepository = FakeJobRepository().apply {
            getPlanResult = { testPlan() }
            deleteResult = { }
        }
        val viewModel = JdAnalyzerResultViewModel(42, jobRepository, FakeRoadmapRepository(), FakeCareerPathRepository())

        var deleted = false
        viewModel.delete(onDeleted = { deleted = true })

        assertTrue(deleted)
        assertEquals(42, jobRepository.lastDeletedJdId)
    }
}

private fun testPlan() = JobPreparationPlanDto(
    id = 1,
    jdId = 42,
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

private fun testRoadmap() = RoadmapDto(
    id = 1,
    jdId = 42,
    durationDays = 14,
    status = "active",
    tasks = emptyList(),
)
