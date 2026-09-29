package com.aiplacementos.app.ui.jobprep

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiplacementos.app.data.remote.AssessmentBlueprintDto
import com.aiplacementos.app.data.remote.AssessmentDto
import com.aiplacementos.app.data.remote.AssessmentHistoryItemDto
import com.aiplacementos.app.data.remote.AssessmentResultDto
import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.data.remote.CareerPathProjectDto
import com.aiplacementos.app.data.remote.CareerPathSummaryDto
import com.aiplacementos.app.data.remote.InterviewPrepCategorySummaryDto
import com.aiplacementos.app.data.remote.JobDescriptionListItemDto
import com.aiplacementos.app.data.remote.JobDescriptionParseResponseDto
import com.aiplacementos.app.data.remote.JobMatchDto
import com.aiplacementos.app.data.remote.JobPreparationPlanDto
import com.aiplacementos.app.data.remote.JobReadinessDto
import com.aiplacementos.app.data.remote.JobSummaryDto
import com.aiplacementos.app.data.remote.NextQuestionResponseDto
import com.aiplacementos.app.data.remote.RoadmapDto
import com.aiplacementos.app.data.remote.RoadmapTaskDto
import com.aiplacementos.app.data.remote.RoleOverviewDto
import com.aiplacementos.app.data.remote.SkillItemDto
import com.aiplacementos.app.domain.repository.AssessmentRepository
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.domain.repository.JobRepository
import com.aiplacementos.app.domain.repository.RoadmapRepository
import com.aiplacementos.app.ui.theme.AiPlacementOsTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Add to my Career Path", then Back from the Career Path -- the screen has to be usable again.
 *
 * Reproduced on an emulator against a live backend before this was written: tap the button, the
 * POST /career-path/{id}/apply-jd/{jd} succeeds in well under a second, the Career Path detail
 * screen opens, press Back once -- and the result screen came back with the button reading
 * "Updating...", disabled, permanently. Tapping it sent nothing. The update had already succeeded
 * (F12-04).
 *
 * NavGraph navigates to the detail screen without popUpTo -- deliberately, because Back must
 * return here -- so this screen's back-stack entry and its view model outlive the navigation, and
 * nothing retired the flag. Its two siblings escape the same shape for two different reasons:
 * generateRoadmap()'s destination pops this screen off the stack, and startPractice() clears its
 * own flag.
 *
 * The view-model tests cover [JobMatchPrepResultViewModel.onScreenResumed] itself. What only a
 * rendered test covers is the whole loop: that the screen calls it, that a real NavHost really
 * does keep this entry alive across the navigation, and that Back lands on a screen the student
 * can use. So this runs the real screen and view model inside a NavHost, navigates for real, and
 * presses the system Back button.
 */
@RunWith(AndroidJUnit4::class)
class JobMatchPrepCareerPathLifecycleTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private companion object {
        const val JD_ID = 42
        const val PATH_ID = 7
        const val BUTTON = "Add to my Career Path"
        const val BUSY = "Updating..."
    }

    /** Counts the calls the button makes, and can hold one open to model a slow network. */
    private class Careers : CareerPathRepository {
        val applyJdCalls = AtomicInteger(0)

        /** Completed by default: a normal, fast update. Replaced to hold one in flight. */
        @Volatile
        var release: CompletableDeferred<Unit> = CompletableDeferred(Unit)

        override suspend fun getCurrent(): CareerPathDto = path()
        override suspend fun applyJd(pathId: Int, jdId: Int): CareerPathDto {
            applyJdCalls.incrementAndGet()
            release.await()
            return path()
        }

        override suspend fun getRoleCatalog(category: String?, query: String?) = emptyList<Nothing>()
        override suspend fun getRoleCategories() = emptyList<String>()
        override suspend fun generate(roleId: Int?, customRole: String?) = unreached()
        override suspend fun getAll(): List<CareerPathSummaryDto> = emptyList()
        override suspend fun getPath(pathId: Int) = unreached()
        override suspend fun setCurrent(pathId: Int) = unreached()
        override suspend fun refresh(pathId: Int) = unreached()
        override suspend fun getProjects(pathId: Int): List<CareerPathProjectDto> = emptyList()
        override suspend fun practiceSkillItem(itemId: Int): Int = unreached()
        override suspend fun markSkillItemStatus(itemId: Int, status: String): SkillItemDto = unreached()
        override suspend fun markProjectStatus(projectId: Int, status: String): CareerPathProjectDto = unreached()
    }

    private class Jobs(private val plan: JobPreparationPlanDto) : JobRepository {
        override suspend fun getPreparationPlan(jdId: Int) = plan
        override suspend fun listJobDescriptions(): List<JobDescriptionListItemDto> = emptyList()
        override suspend fun parseJobDescription(rawText: String): JobDescriptionParseResponseDto = unreached()
        override suspend fun uploadJobDescription(fileName: String, bytes: ByteArray):
            JobDescriptionParseResponseDto = unreached()
        override suspend fun matchJob(jdId: Int): JobMatchDto = unreached()
        override suspend fun generatePreparationPlan(jdId: Int, companyName: String?, jobTitle: String?) = unreached()
        override suspend fun deleteJobDescription(jdId: Int) = unreached()
    }

    private class Roadmaps : RoadmapRepository {
        override suspend fun generateRoadmap(jdId: Int?, durationDays: Int): RoadmapDto = unreached()
        override suspend fun getCurrentRoadmap(jdId: Int?): RoadmapDto? = null
        override suspend fun updateTaskStatus(taskId: Int, status: String): RoadmapTaskDto = unreached()
        override suspend fun reassessRoadmap(roadmapId: Int): RoadmapDto = unreached()
    }

    private class Assessments : AssessmentRepository {
        override suspend fun createBlueprint(roleId: Int?, jdId: Int?, category: String?):
            AssessmentBlueprintDto = unreached()
        override suspend fun startAssessment(blueprintId: Int): AssessmentDto = unreached()
        override suspend fun nextQuestion(assessmentId: Int): NextQuestionResponseDto = unreached()
        override suspend fun submitAnswer(
            assessmentId: Int,
            questionId: Int,
            studentAnswer: String,
            timeTakenSeconds: Int?,
        ) = unreached()
        override suspend fun getResult(assessmentId: Int): AssessmentResultDto = unreached()
        override suspend fun getHistory(): List<AssessmentHistoryItemDto> = emptyList()
    }

    private val careers = Careers()

    private fun viewModel() = JobMatchPrepResultViewModel(
        jdId = JD_ID,
        jobRepository = Jobs(plan()),
        roadmapRepository = Roadmaps(),
        assessmentRepository = Assessments(),
        careerPathRepository = careers,
    )

    /**
     * The real screen inside a NavHost, wired exactly as NavGraph wires it -- navigate to the
     * detail route with no popUpTo. The view model is seeded into the back-stack entry under the
     * same key the screen's `viewModel(key = ...)` resolves, so the screen adopts it and nothing
     * about the screen changes for the test.
     */
    @Composable
    private fun Graph(viewModel: JobMatchPrepResultViewModel) {
        AiPlacementOsTheme {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "result") {
                composable("result") { entry ->
                    remember(entry) {
                        ViewModelProvider(entry, viewModelFactory { initializer { viewModel } })
                            .get("job-match-prep-result-$JD_ID", JobMatchPrepResultViewModel::class.java)
                    }
                    JobMatchPrepResultScreen(
                        jdId = JD_ID,
                        onBack = {},
                        onGenerateRoadmap = {},
                        onStartPractice = {},
                        onDeleted = {},
                        onCareerPathUpdated = { pathId -> navController.navigate("career-path/$pathId") },
                        onUploadResume = {},
                    )
                }
                composable("career-path/{pathId}") { entry ->
                    Text("Career Path ${entry.arguments?.getString("pathId")}")
                }
            }
        }
    }

    private fun show(viewModel: JobMatchPrepResultViewModel) =
        composeRule.setContent { Graph(viewModel) }

    private fun waitForText(text: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tapUpdate() {
        composeRule.onNodeWithText(BUTTON).performScrollTo().performClick()
    }

    private fun pressBack() {
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    @Test
    fun backFromTheCareerPathLeavesTheButtonUsableAndASecondTapSendsOneMoreRequest() {
        show(viewModel())
        waitForText(BUTTON)

        tapUpdate()

        waitForText("Career Path $PATH_ID")
        composeRule.onNodeWithText("Career Path $PATH_ID").assertIsDisplayed()
        assertEquals(1, careers.applyJdCalls.get())

        pressBack()

        // The whole of F12-04: this is where the button used to come back reading "Updating...",
        // disabled, with the update already done.
        waitForText(BUTTON)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(BUSY).assertCountEquals(0)
        composeRule.onNodeWithText(BUTTON).performScrollTo().assertIsEnabled().assertHasClickAction()

        tapUpdate()

        waitForText("Career Path $PATH_ID")
        assertEquals("the second tap sends exactly one more request", 2, careers.applyJdCalls.get())
    }

    @Test
    fun tappingAgainWhileTheUpdateIsGenuinelyInFlightSendsOneRequest() {
        careers.release = CompletableDeferred()
        show(viewModel())
        waitForText(BUTTON)

        tapUpdate()
        waitForText(BUSY)

        // Three more taps on a button the student can see is busy.
        repeat(3) { composeRule.onNodeWithText(BUSY).performScrollTo().performClick() }
        composeRule.waitForIdle()

        assertEquals("a genuinely in-flight update is not duplicated", 1, careers.applyJdCalls.get())

        careers.release.complete(Unit)
        waitForText("Career Path $PATH_ID")
        assertEquals(1, careers.applyJdCalls.get())
    }

    @Test
    fun backDoesNotReopenTheCareerPath() {
        show(viewModel())
        waitForText(BUTTON)
        tapUpdate()
        waitForText("Career Path $PATH_ID")

        pressBack()

        // A navigation driven by state that is never consumed re-fires on the recomposition Back
        // causes, and the student cannot get off the detail screen.
        waitForText(BUTTON)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Career Path $PATH_ID").assertCountEquals(0)
    }
}

private fun unreached(): Nothing = throw UnsupportedOperationException("not part of this flow")

private fun plan() = JobPreparationPlanDto(
    id = 1,
    jdId = 42,
    companyName = "Acme Analytics",
    jobTitle = "Data Analyst",
    jobSummary = JobSummaryDto(
        companyName = "Acme Analytics",
        jobTitle = "Data Analyst",
        experienceExpectation = null,
        educationEligibility = null,
        location = null,
        employmentType = null,
    ),
    requiredSkills = emptyList(),
    preparationGaps = emptyList(),
    preparationPlan = emptyList(),
    interviewPrep = emptyList(),
    gapGuidance = null,
    resumeRecommendations = null,
    projectConnections = null,
    createdAt = "2026-01-01T00:00:00Z",
)

private fun path() = CareerPathDto(
    id = 7,
    roleFamilyKey = "data_analyst",
    displayRoleName = "Data Analyst",
    isPrimary = true,
    generatedVia = "catalog",
    roleOverview = RoleOverviewDto(
        displayName = "Data Analyst",
        category = "Data & AI",
        shortExplanation = "Turns data into insights.",
        responsibilities = listOf("Query databases"),
        coreSkills = listOf("SQL"),
        tools = listOf("Power BI"),
        difficulty = "beginner-friendly",
        entryLevelExpectations = "Basic SQL.",
        interviewFocus = listOf("SQL"),
        portfolioExpectations = "One dashboard.",
    ),
    phases = emptyList(),
    skillGaps = emptyList(),
    interviewPrep = emptyList<InterviewPrepCategorySummaryDto>(),
    jobReadiness = JobReadinessDto(
        overallPercent = 10,
        skillsPercent = 10,
        projectsPercent = 0,
        topFocusAreas = emptyList(),
    ),
    createdAt = "2026-01-01T00:00:00Z",
    updatedAt = "2026-01-01T00:00:00Z",
)
