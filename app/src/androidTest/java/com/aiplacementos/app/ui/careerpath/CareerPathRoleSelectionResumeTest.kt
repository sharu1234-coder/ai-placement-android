package com.aiplacementos.app.ui.careerpath

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.data.remote.CareerPathProjectDto
import com.aiplacementos.app.data.remote.CareerPathSummaryDto
import com.aiplacementos.app.data.remote.InterviewPrepCategorySummaryDto
import com.aiplacementos.app.data.remote.JobReadinessDto
import com.aiplacementos.app.data.remote.RoleCatalogEntryDto
import com.aiplacementos.app.data.remote.RoleOverviewDto
import com.aiplacementos.app.data.remote.SkillItemDto
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.ui.theme.AiPlacementOsTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The screen retires a finished generation's building state when it is resumed.
 *
 * Reproduced on an emulator against a live backend before this was written: choose a curated
 * role, let the generation succeed, land on the detail screen, press Back once -- and the role
 * selection screen came back cycling "Analyzing your profile..." with no catalog, no search and
 * no custom-role field, permanently. NavGraph navigates without popUpTo, so this screen's
 * back-stack entry and its view model outlive the navigation, and nothing retired the flag.
 *
 * The view-model tests cover [CareerPathRoleSelectionViewModel.onScreenResumed] itself. What
 * only a rendered test covers is that the screen actually calls it -- the same gap that left
 * RoleCard's onClick unwired and generationError undisplayed for a phase each. So this renders
 * the real screen over a view model already left in the post-success state, and asserts the
 * catalog is what the student sees.
 */
@RunWith(AndroidJUnit4::class)
class CareerPathRoleSelectionResumeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val roles = listOf(
        RoleCatalogEntryDto("data_analyst", "Data Analyst", "Data & AI", "Turns data into insights."),
    )

    /** Minimal stand-in: only the three calls this screen makes are ever reached. */
    private class StubRepository(
        private val roles: List<RoleCatalogEntryDto>,
        private val onGenerate: suspend () -> CareerPathDto,
    ) : CareerPathRepository {
        override suspend fun getRoleCatalog(category: String?, query: String?) = roles
        override suspend fun getRoleCategories() = listOf("Data & AI")
        override suspend fun generate(roleId: Int?, customRole: String?) = onGenerate()
        override suspend fun getAll(): List<CareerPathSummaryDto> = emptyList()
        override suspend fun getCurrent(): CareerPathDto? = null
        override suspend fun getPath(pathId: Int) = throw UnsupportedOperationException()
        override suspend fun setCurrent(pathId: Int) = throw UnsupportedOperationException()
        override suspend fun refresh(pathId: Int, idempotencyKey: String?) =
            throw UnsupportedOperationException()
        override suspend fun getProjects(pathId: Int): List<CareerPathProjectDto> = emptyList()
        override suspend fun practiceSkillItem(itemId: Int) = throw UnsupportedOperationException()
        override suspend fun markSkillItemStatus(itemId: Int, status: String): SkillItemDto =
            throw UnsupportedOperationException()
        override suspend fun markProjectStatus(projectId: Int, status: String): CareerPathProjectDto =
            throw UnsupportedOperationException()
        override suspend fun applyJd(pathId: Int, jdId: Int) = throw UnsupportedOperationException()
    }

    private fun generatedPath() = CareerPathDto(
        id = 1,
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

    /**
     * Renders the real screen over a view model seeded into the store under the same key
     * `viewModel()` resolves, so the screen adopts it instead of building its own. Nothing about
     * the screen changes for the test.
     */
    /** viewModelScope dispatches on Dispatchers.Main.immediate, so this has to run on the main
     * thread for the launch to execute inline and leave a deterministic state to render over. */
    private fun startGenerationOnMainThread(viewModel: CareerPathRoleSelectionViewModel) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.selectCustomRole("Data Analyst")
        }
    }

    private fun showScreenWith(viewModel: CareerPathRoleSelectionViewModel) {
        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
        ViewModelProvider(owner, viewModelFactory { initializer { viewModel } })
            .get(CareerPathRoleSelectionViewModel::class.java)

        composeRule.setContent {
            AiPlacementOsTheme {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    CareerPathRoleSelectionScreen(onBack = {}, onGenerated = {})
                }
            }
        }
    }

    @Test
    fun aScreenResumedAfterASuccessfulGenerationShowsTheCatalogAgain() {
        val viewModel = CareerPathRoleSelectionViewModel(StubRepository(roles) { generatedPath() })
        // Exactly the state Back used to come back to: the generation succeeded and navigated
        // away, leaving isGenerating true because clearing it there would flash the catalog
        // over the outgoing screen.
        startGenerationOnMainThread(viewModel)
        assertTrue(
            "precondition: the screen must start from the stuck post-success state",
            (viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded).isGenerating,
        )

        showScreenWith(viewModel)

        composeRule.onNodeWithText("Data Analyst").assertIsDisplayed()
        composeRule.onNodeWithText("Search roles...").assertIsDisplayed()
        composeRule.onAllNodesWithText("Building your career path...").assertCountEquals(0)
    }

    @Test
    fun aScreenResumedWhileAGenerationIsStillRunningKeepsShowingTheBuildingState() {
        val inFlight = CompletableDeferred<Unit>()
        val viewModel = CareerPathRoleSelectionViewModel(
            StubRepository(roles) {
                inFlight.await()
                generatedPath()
            },
        )
        startGenerationOnMainThread(viewModel)
        assertTrue(
            "precondition: a request really is in flight",
            (viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded).isGenerating,
        )

        showScreenWith(viewModel)

        // Backgrounding the app mid-generation and returning must not hand the student a
        // catalog while the request is still running -- that is also what would let them start
        // a second generation past the in-flight guard.
        composeRule.onAllNodesWithText("Search roles...").assertCountEquals(0)
        inFlight.complete(Unit)
    }
}
