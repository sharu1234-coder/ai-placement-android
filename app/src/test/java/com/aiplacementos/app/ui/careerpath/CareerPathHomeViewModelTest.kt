package com.aiplacementos.app.ui.careerpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.data.remote.CareerPathPhaseDto
import com.aiplacementos.app.data.remote.CareerPathProjectDto
import com.aiplacementos.app.data.remote.CareerPathSummaryDto
import com.aiplacementos.app.data.remote.InterviewPrepCategorySummaryDto
import com.aiplacementos.app.data.remote.JobReadinessDto
import com.aiplacementos.app.data.remote.ResumeConnectionDto
import com.aiplacementos.app.data.remote.RoleOverviewDto
import com.aiplacementos.app.data.remote.SkillGapItemDto
import com.aiplacementos.app.data.remote.SkillItemDto
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Shared sample builder reused by every Career Path ViewModel test file in this package --
 * same precedent as AtsCheckResultViewModelTest's internal sampleReport(). */
internal fun sampleSkillItem(
    id: Int = 1,
    phaseKey: String = "foundations",
    skillKey: String = "sql_basics",
    title: String = "SQL Basics",
    status: String = "not_started",
    hasPractice: Boolean = false,
) = SkillItemDto(
    id = id,
    phaseKey = phaseKey,
    skillKey = skillKey,
    title = title,
    level = "beginner",
    whyItMatters = "Core to the role.",
    orderIndex = 0,
    status = status,
    completedVia = null,
    hasPractice = hasPractice,
)

internal fun sampleProject(
    id: Int = 1,
    projectKey: String = "sales_dashboard",
    title: String = "Sales Dashboard",
    difficulty: String = "beginner",
    status: String = "not_started",
) = CareerPathProjectDto(
    id = id,
    projectKey = projectKey,
    title = title,
    difficulty = difficulty,
    skillsDemonstrated = listOf("SQL", "Power BI"),
    technologies = listOf("SQL", "Power BI"),
    problemStatement = "Build a dashboard.",
    expectedOutcome = "A working dashboard.",
    whatShouldBeIncluded = "KPIs and filters.",
    whyRecruitersValue = "Common analyst deliverable.",
    resumeBulletSuggestion = "Built a dashboard tracking KPIs.",
    interviewQuestions = listOf("How did you pick your KPIs?"),
    orderIndex = 0,
    status = status,
)

internal fun sampleCareerPath(
    id: Int = 1,
    displayRoleName: String = "Data Analyst",
    isPrimary: Boolean = true,
    generatedVia: String = "catalog",
    phases: List<CareerPathPhaseDto> = listOf(
        CareerPathPhaseDto(
            phaseKey = "foundations",
            title = "Foundations",
            objective = "Build the basics.",
            orderIndex = 0,
            status = "not_started",
            skillItems = listOf(sampleSkillItem()),
            projects = emptyList(),
        ),
        CareerPathPhaseDto(
            phaseKey = "projects",
            title = "Projects",
            objective = "Prove your skills.",
            orderIndex = 2,
            status = "not_started",
            skillItems = emptyList(),
            projects = listOf(sampleProject()),
        ),
    ),
    skillGaps: List<SkillGapItemDto> = listOf(SkillGapItemDto("SQL", "strong", "Present on your resume.")),
    resumeConnection: ResumeConnectionDto? = null,
    interviewPrep: List<InterviewPrepCategorySummaryDto> = emptyList(),
    jobReadiness: JobReadinessDto = JobReadinessDto(
        overallPercent = 42,
        skillsPercent = 50,
        projectsPercent = 0,
        resumePercent = 60,
        practicePercent = 0,
        interviewPercent = 0,
        topFocusAreas = listOf("Complete more skills in your Career Path"),
    ),
) = CareerPathDto(
    id = id,
    roleId = null,
    customRole = null,
    roleFamilyKey = "data_analyst",
    displayRoleName = displayRoleName,
    isPrimary = isPrimary,
    generatedVia = generatedVia,
    roleOverview = RoleOverviewDto(
        displayName = displayRoleName,
        category = "Data & AI",
        shortExplanation = "Turns data into insights.",
        responsibilities = listOf("Query databases"),
        coreSkills = listOf("SQL", "Excel"),
        tools = listOf("Power BI"),
        difficulty = "beginner-friendly",
        entryLevelExpectations = "Basic SQL.",
        interviewFocus = listOf("SQL"),
        portfolioExpectations = "One dashboard.",
    ),
    phases = phases,
    skillGaps = skillGaps,
    resumeConnection = resumeConnection,
    interviewPrep = interviewPrep,
    jobReadiness = jobReadiness,
    createdAt = "2026-01-01T00:00:00Z",
    updatedAt = "2026-01-01T00:00:00Z",
)

internal fun sampleSummary(id: Int = 1, displayRoleName: String = "Data Analyst", isPrimary: Boolean = true) =
    CareerPathSummaryDto(
        id = id,
        roleFamilyKey = "data_analyst",
        displayRoleName = displayRoleName,
        isPrimary = isPrimary,
        readinessPercent = 30,
        completedItems = 2,
        totalItems = 10,
        generatedVia = "catalog",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )

class CareerPathHomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows Success when a current career path exists`() {
        val repository = FakeCareerPathRepository().apply {
            currentResult = { sampleCareerPath() }
            allResult = { listOf(sampleSummary()) }
        }
        val viewModel = CareerPathHomeViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is CareerPathHomeUiState.Success)
        assertEquals("Data Analyst", (state as CareerPathHomeUiState.Success).current.displayRoleName)
        assertEquals(1, state.savedCount)
    }

    @Test
    fun `shows NoPath with saved count when no current path exists`() {
        val repository = FakeCareerPathRepository().apply {
            currentResult = { null }
            allResult = { listOf(sampleSummary(), sampleSummary(id = 2, displayRoleName = "SDE")) }
        }
        val viewModel = CareerPathHomeViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is CareerPathHomeUiState.NoPath)
        assertEquals(2, (state as CareerPathHomeUiState.NoPath).savedCount)
    }

    @Test
    fun `surfaces an error message on failure`() {
        val repository = FakeCareerPathRepository().apply {
            currentResult = { throw java.io.IOException("down") }
        }
        val viewModel = CareerPathHomeViewModel(repository)

        assertTrue(viewModel.uiState.value is CareerPathHomeUiState.Error)
    }
}
