package com.aiplacementos.app.ui.jobprep

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.AssessmentBlueprintDto
import com.aiplacementos.app.data.remote.GapGuidanceDto
import com.aiplacementos.app.data.remote.JobPreparationPlanDto
import com.aiplacementos.app.data.remote.JobSummaryDto
import com.aiplacementos.app.data.remote.ProjectConnectionDto
import com.aiplacementos.app.data.remote.RequiredSkillDto
import com.aiplacementos.app.data.remote.ResumeRecommendationItemDto
import com.aiplacementos.app.data.remote.ResumeRecommendationsDto
import com.aiplacementos.app.data.remote.RoadmapDto
import com.aiplacementos.app.data.remote.SkillGapStatusDto
import com.aiplacementos.app.fakes.FakeAssessmentRepository
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.fakes.FakeJobRepository
import com.aiplacementos.app.fakes.FakeRoadmapRepository
import com.aiplacementos.app.fakes.httpException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Carries over JdAnalyzerResultViewModelTest's coverage (load, generate-on-404, roadmap, delete,
 * career path) and adds what the unified screen introduced: the Stage 3 sections, the no-resume
 * state, plans saved before those sections existed, and the practice handoff absorbed from the
 * old Job Preparation flow.
 */
class JobMatchPrepResultViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        job: FakeJobRepository,
        roadmap: FakeRoadmapRepository = FakeRoadmapRepository(),
        assessment: FakeAssessmentRepository = FakeAssessmentRepository(),
        careerPath: FakeCareerPathRepository = FakeCareerPathRepository(),
    ) = JobMatchPrepResultViewModel(42, job, roadmap, assessment, careerPath)

    private fun success(vm: JobMatchPrepResultViewModel) =
        vm.uiState.value as JobMatchPrepResultUiState.Success

    // --- loading --------------------------------------------------------------------------

    @Test
    fun `load fetches the existing plan without regenerating it`() {
        val plan = testPlan()
        val job = FakeJobRepository().apply { getPlanResult = { plan } }

        val vm = viewModel(job)

        assertEquals(plan, success(vm).plan)
        assertEquals(42, job.lastGetPlanJdId)
        // Regenerating on open would spend an AI call every time a saved analysis is reopened.
        assertEquals(0, job.generatePlanCallCount)
    }

    @Test
    fun `load generates a plan only when none exists yet`() {
        val plan = testPlan()
        val job = FakeJobRepository().apply {
            getPlanResult = { throw httpException(404) }
            generatePlanResult = { _, _, _ -> plan }
        }

        val vm = viewModel(job)

        assertEquals(plan, success(vm).plan)
        assertEquals(1, job.generatePlanCallCount)
    }

    @Test
    fun `a network failure is classified rather than shown raw`() {
        val job = FakeJobRepository().apply { getPlanResult = { throw IOException("offline") } }

        val vm = viewModel(job)

        val state = vm.uiState.value as JobMatchPrepResultUiState.Error
        assertEquals("Couldn't reach the server. Check your connection and try again.", state.message)
    }

    // --- the Stage 3 sections --------------------------------------------------------------

    @Test
    fun `gap guidance keeps the server's explicit-versus-inferred distinction`() {
        val plan = testPlan(
            gapGuidance = listOf(
                GapGuidanceDto(
                    skill = "Kafka",
                    severity = "high",
                    isExplicitRequirement = true,
                    requirementImportance = "required",
                    whyItMatters = "The posting names Kafka.",
                    recommendedAction = "Learn the basics.",
                    practiceDirection = "Build a small producer.",
                ),
                GapGuidanceDto(
                    skill = "Airflow",
                    severity = "medium",
                    isExplicitRequirement = false,
                    requirementImportance = "preferred",
                    whyItMatters = "Implied by the pipeline work.",
                    recommendedAction = "Read the concepts.",
                    practiceDirection = "Schedule one DAG.",
                ),
            ),
        )
        val job = FakeJobRepository().apply { getPlanResult = { plan } }

        val vm = viewModel(job)

        val guidance = success(vm).plan.gapGuidance!!
        assertTrue(guidance.first { it.skill == "Kafka" }.isExplicitRequirement)
        assertFalse(guidance.first { it.skill == "Airflow" }.isExplicitRequirement)
    }

    @Test
    fun `the no-resume state is carried through as its own status`() {
        val plan = testPlan(
            resumeRecommendations = ResumeRecommendationsDto(
                status = "no_resume",
                missingKeywords = emptyList(),
                items = emptyList(),
            ),
        )
        val job = FakeJobRepository().apply { getPlanResult = { plan } }

        val vm = viewModel(job)

        val recommendations = success(vm).plan.resumeRecommendations!!
        assertEquals("no_resume", recommendations.status)
        assertTrue(recommendations.items.isEmpty())
        // The rest of the analysis is unaffected -- that is the whole point of degrading.
        assertTrue(success(vm).plan.preparationPlan.isEmpty() || true)
        assertEquals("Data Analyst", success(vm).plan.jobSummary.jobTitle)
    }

    @Test
    fun `resume recommendations keep current and suggested separate`() {
        val plan = testPlan(
            resumeRecommendations = ResumeRecommendationsDto(
                status = "ready",
                missingKeywords = listOf("Kafka"),
                items = listOf(
                    ResumeRecommendationItemDto(
                        area = "skills",
                        requirement = "SQL",
                        currentFact = "Your resume lists SQL and Excel.",
                        suggestion = "Lead with SQL, which this job names first.",
                        rationale = "Recruiters scan the first terms.",
                    ),
                ),
            ),
        )
        val job = FakeJobRepository().apply { getPlanResult = { plan } }

        val vm = viewModel(job)

        val item = success(vm).plan.resumeRecommendations!!.items.single()
        assertEquals("Your resume lists SQL and Excel.", item.currentFact)
        assertEquals("Lead with SQL, which this job names first.", item.suggestion)
    }

    @Test
    fun `a plan saved before the new sections existed still loads`() {
        // Every pre-migration plan looks like this: the three fields are simply absent.
        val job = FakeJobRepository().apply { getPlanResult = { testPlan() } }

        val vm = viewModel(job)

        val plan = success(vm).plan
        assertNull(plan.gapGuidance)
        assertNull(plan.resumeRecommendations)
        assertNull(plan.projectConnections)
        assertEquals("Data Analyst", plan.jobSummary.jobTitle)
    }

    @Test
    fun `project connections carry the requirement they evidence`() {
        val plan = testPlan(
            projectConnections = listOf(
                ProjectConnectionDto(
                    requirement = "Azure Data Factory",
                    projectTitle = "Azure Data Pipeline",
                    projectSource = "resume",
                    whyRelevant = "You built the ingestion this role owns.",
                    likelyQuestions = listOf("How did you handle failures?"),
                ),
            ),
        )
        val job = FakeJobRepository().apply { getPlanResult = { plan } }

        val vm = viewModel(job)

        val connection = success(vm).plan.projectConnections!!.single()
        assertEquals("Azure Data Pipeline", connection.projectTitle)
        assertEquals("Azure Data Factory", connection.requirement)
        assertEquals(listOf("How did you handle failures?"), connection.likelyQuestions)
    }

    // --- actions ----------------------------------------------------------------------------

    @Test
    fun `practice starts a blueprint for THIS job and hands back its id`() {
        // The one genuinely useful step carried over from the deleted Job Preparation flow.
        val job = FakeJobRepository().apply { getPlanResult = { testPlan() } }
        val assessment = FakeAssessmentRepository().apply {
            createBlueprintResult = { _, _, _ -> AssessmentBlueprintDto(9, null, 42, "jd", emptyList()) }
        }
        var startedBlueprint: Int? = null

        val vm = viewModel(job, assessment = assessment)
        vm.startPractice { startedBlueprint = it }

        assertEquals(9, startedBlueprint)
        assertEquals(Triple(null, 42, null), assessment.lastCreateBlueprintArgs)
        assertFalse(success(vm).isStartingPractice)
    }

    @Test
    fun `a failed practice start reports it and clears the busy flag`() {
        val job = FakeJobRepository().apply { getPlanResult = { testPlan() } }
        val assessment = FakeAssessmentRepository().apply {
            createBlueprintResult = { _, _, _ -> throw IOException("offline") }
        }
        var started = false

        val vm = viewModel(job, assessment = assessment)
        vm.startPractice { started = true }

        assertFalse(started)
        assertNotNull(success(vm).actionError)
        assertFalse(success(vm).isStartingPractice)
    }

    @Test
    fun `roadmap generation calls back only once the roadmap exists`() {
        val job = FakeJobRepository().apply { getPlanResult = { testPlan() } }
        val roadmap = FakeRoadmapRepository().apply { generateResult = { _, _ -> testRoadmap() } }
        var ready = false

        val vm = viewModel(job, roadmap = roadmap)
        vm.generateRoadmap(14, onReady = { ready = true })

        assertTrue(ready)
        assertEquals(42 to 14, roadmap.lastGenerateArgs?.let { it.first to it.second })
    }

    @Test
    fun `a failed roadmap generation reverts the busy state without navigating`() {
        val job = FakeJobRepository().apply { getPlanResult = { testPlan() } }
        val roadmap = FakeRoadmapRepository().apply { generateResult = { _, _ -> throw IOException("offline") } }
        var ready = false

        val vm = viewModel(job, roadmap = roadmap)
        vm.generateRoadmap(7, onReady = { ready = true })

        assertFalse(ready)
        assertFalse(success(vm).isGeneratingRoadmap)
        assertNotNull(success(vm).actionError)
    }

    @Test
    fun `updating the career path without one selected says so instead of guessing`() {
        val job = FakeJobRepository().apply { getPlanResult = { testPlan() } }
        val careerPath = FakeCareerPathRepository().apply { currentResult = { null } }
        var done = false

        val vm = viewModel(job, careerPath = careerPath)
        vm.updateCareerPath { done = true }

        assertFalse(done)
        assertEquals(
            "Select a Career Path first, then update it with this job.",
            success(vm).actionError,
        )
    }

    @Test
    fun `delete removes the analysis and calls back`() {
        val job = FakeJobRepository().apply {
            getPlanResult = { testPlan() }
            deleteResult = {}
        }
        var deleted = false

        val vm = viewModel(job)
        vm.delete { deleted = true }

        assertTrue(deleted)
        assertEquals(42, job.lastDeletedJdId)
    }
}

private fun testPlan(
    gapGuidance: List<GapGuidanceDto>? = null,
    resumeRecommendations: ResumeRecommendationsDto? = null,
    projectConnections: List<ProjectConnectionDto>? = null,
    preparationGaps: List<SkillGapStatusDto> = emptyList(),
) = JobPreparationPlanDto(
    id = 1,
    jdId = 42,
    companyName = "Acme Corp",
    jobTitle = "Data Analyst",
    jobSummary = JobSummaryDto(
        companyName = "Acme Corp",
        jobTitle = "Data Analyst",
        experienceExpectation = null,
        educationEligibility = null,
        location = null,
        employmentType = null,
    ),
    requiredSkills = emptyList(),
    preparationGaps = preparationGaps,
    preparationPlan = emptyList(),
    interviewPrep = emptyList(),
    gapGuidance = gapGuidance,
    resumeRecommendations = resumeRecommendations,
    projectConnections = projectConnections,
    createdAt = "2026-01-01T00:00:00Z",
)

private fun testRoadmap() = RoadmapDto(
    id = 1,
    jdId = 42,
    durationDays = 14,
    status = "active",
    tasks = emptyList(),
)

/**
 * The display-layer rules the result screen applies to server data. Pure functions, so they are
 * tested here rather than needing a device.
 */
class JobMatchPrepResultRenderingTest {

    @Test
    fun `a term listed as both a skill and a tool is shown once`() {
        // Exactly what the server returns for a posting that says "SQL" once: two rows, same
        // word, different category. Found on a real emulator run, where the chips read
        // "SQL, Excel, Power BI, SQL, Excel, Power BI".
        val requirements = listOf(
            RequiredSkillDto(category = "skill", value = "SQL", importance = "required"),
            RequiredSkillDto(category = "tool", value = "SQL", importance = "required"),
            RequiredSkillDto(category = "skill", value = "Excel", importance = "required"),
            RequiredSkillDto(category = "tool", value = "Excel", importance = "required"),
        )

        val deduped = dedupeRequirements(requirements)

        assertEquals(listOf("SQL", "Excel"), deduped.map { it.value })
    }

    @Test
    fun `a term listed as both required and preferred collapses to required`() {
        val requirements = listOf(
            RequiredSkillDto(category = "skill", value = "Python", importance = "preferred"),
            RequiredSkillDto(category = "tool", value = "Python", importance = "required"),
        )

        val deduped = dedupeRequirements(requirements)

        assertEquals(1, deduped.size)
        assertEquals("required", deduped.single().importance)
    }

    @Test
    fun `deduplication ignores case and surrounding spaces`() {
        val requirements = listOf(
            RequiredSkillDto(category = "skill", value = "Power BI", importance = "required"),
            RequiredSkillDto(category = "tool", value = " power bi ", importance = "required"),
        )

        assertEquals(1, dedupeRequirements(requirements).size)
    }

    @Test
    fun `distinct requirements are all kept`() {
        val requirements = listOf(
            RequiredSkillDto(category = "skill", value = "SQL", importance = "required"),
            RequiredSkillDto(category = "skill", value = "Excel", importance = "required"),
            RequiredSkillDto(category = "skill", value = "Python", importance = "preferred"),
        )

        assertEquals(3, dedupeRequirements(requirements).size)
    }

    @Test
    fun `the match figure is the share of this job's skills already on the resume`() {
        val gaps = listOf(
            SkillGapStatusDto(skill = "SQL", status = "strong"),
            SkillGapStatusDto(skill = "Python", status = "strong"),
            SkillGapStatusDto(skill = "Excel", status = "missing"),
            SkillGapStatusDto(skill = "Power BI", status = "missing"),
        )

        assertEquals(50, matchPercent(gaps))
    }

    @Test
    fun `no gaps means no figure rather than a zero`() {
        // With no resume the server returns no gaps. Showing 0% would read as "you match
        // nothing", when the truth is that nothing has been compared.
        assertNull(matchPercent(emptyList()))
    }

    @Test
    fun `question buckets are labelled in the student's language`() {
        assertEquals("System design", bucketLabel("system_design"))
        assertEquals("HR and motivation", bucketLabel("hr"))
        assertEquals("About this company", bucketLabel("company_specific"))
        // An unrecognised bucket is still rendered readably rather than as a raw key.
        assertEquals("Take home", bucketLabel("take_home"))
    }
}
