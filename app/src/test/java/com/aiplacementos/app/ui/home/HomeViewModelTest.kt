package com.aiplacementos.app.ui.home

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.CareerGoalListItemDto
import com.aiplacementos.app.data.remote.CareerPathSummaryDto
import com.aiplacementos.app.data.remote.CareerTwinDto
import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.data.remote.ProgressDashboardDto
import com.aiplacementos.app.data.remote.RecommendedActionDto
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.fakes.FakeCareerGoalRepository
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.fakes.FakeCareerTwinRepository
import com.aiplacementos.app.fakes.FakeNextBestActionRepository
import com.aiplacementos.app.fakes.FakeProfileRepository
import com.aiplacementos.app.fakes.FakeProgressRepository
import com.aiplacementos.app.ui.common.UiState
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Home as the career spine.
 *
 * Two rules run through every test below and they are the reason the ViewModel exposes a
 * [SectionState] per section rather than a bare nullable:
 *
 *     a measured zero is a number      0% coverage is a real reading and must survive as 0
 *     an absent measurement is null    and must never be rendered as 0
 *     a failed request is neither      it is UNAVAILABLE, and says so in its own words
 *
 * The third is the one a dashboard gets wrong most easily. A dropped connection and a student
 * with no evidence both produce "no number", and collapsing them turns a network blip into a
 * confident statement about someone's career.
 */
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun goal(
        id: Int = 1,
        title: String = "Data Engineer",
        company: String = "Contoso",
        location: String? = "Hyderabad",
        status: String = "active",
        applicationStatus: String? = null,
        preparationState: String = "in_progress",
        updatedAt: String = "2026-03-01T00:00:00Z",
    ) = CareerGoalListItemDto(
        id = id,
        title = title,
        company = company,
        location = location,
        status = status,
        isAnalyzed = true,
        preparationState = preparationState,
        applicationStatus = applicationStatus,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = updatedAt,
    )

    private fun pathSummary(
        preparationPercent: Int? = 60,
        completedItems: Int = 6,
        totalItems: Int = 10,
        isPrimary: Boolean = true,
    ) = CareerPathSummaryDto(
        id = 1,
        roleFamilyKey = "data_engineer",
        displayRoleName = "Data Engineer",
        isPrimary = isPrimary,
        preparationPercent = preparationPercent,
        completedItems = completedItems,
        totalItems = totalItems,
        generatedVia = "catalog",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )

    private fun dashboard(performanceScore: Double? = 0.68) = ProgressDashboardDto(
        performanceScore = performanceScore,
        completedAssessments = 24,
        completedInterviews = 3,
        roadmapTasksDone = 10,
        roadmapTasksTotal = 20,
        topWeaknesses = emptyList(),
        trend = emptyList(),
    )

    private fun twin(
        hasRole: Boolean = true,
        evidenceCoveragePercent: Int? = 73,
    ) = CareerTwinDto(
        hasRole = hasRole,
        roleSource = if (hasRole) "career_path" else "none",
        evidenceCoveragePercent = evidenceCoveragePercent,
    )

    private fun action(
        hasRecommendation: Boolean = true,
        title: String = "Practice technical interview",
        destination: String? = "interview",
    ) = RecommendedActionDto(
        hasRecommendation = hasRecommendation,
        title = title,
        actionType = "practice",
        destination = destination,
    )

    private fun viewModel(
        goals: Result<List<CareerGoalListItemDto>> = Result.success(listOf(goal())),
        paths: Result<List<CareerPathSummaryDto>> = Result.success(listOf(pathSummary())),
        dashboard: Result<ProgressDashboardDto> = Result.success(dashboard()),
        careerTwin: Result<CareerTwinDto> = Result.success(twin()),
        nextAction: Result<RecommendedActionDto> = Result.success(action()),
        profile: FakeProfileRepository = FakeProfileRepository(),
    ): HomeViewModel = HomeViewModel(
        progressRepository = FakeProgressRepository().apply { dashboardResult = dashboard },
        careerPathRepository = FakeCareerPathRepository().apply { allResult = { paths.getOrThrow() } },
        profileRepository = profile,
        careerTwinRepository = FakeCareerTwinRepository().apply { careerTwinResult = { careerTwin.getOrThrow() } },
        careerGoalRepository = FakeCareerGoalRepository().apply { listGoalsResult = { goals.getOrThrow() } },
        nextBestActionRepository = FakeNextBestActionRepository().apply { result = { nextAction.getOrThrow() } },
    )

    /** Loads once, the way the screen's LifecycleResumeEffect does, then reads the result.
     * The ViewModel has no init{} auto-load -- see its own note for why. */
    private fun data(viewModel: HomeViewModel): HomeData {
        viewModel.load()
        return (viewModel.uiState.value as UiState.Success).data
    }

    // ======================================================================================
    // A-C. The active goal
    // ======================================================================================

    @Test
    fun `no career goal reports an empty target rather than inventing one`() {
        val data = data(viewModel(goals = Result.success(emptyList())))

        assertNull(data.activeGoal)
        assertEquals(SectionState.EMPTY, data.goalState)
        assertEquals(0, data.otherActiveGoalCount)
    }

    @Test
    fun `one active goal becomes the target`() {
        val data = data(viewModel())

        assertEquals("Data Engineer", data.activeGoal?.title)
        assertEquals("Contoso", data.activeGoal?.company)
        assertEquals("Hyderabad", data.activeGoal?.location)
        assertEquals(SectionState.AVAILABLE, data.goalState)
        assertEquals(0, data.otherActiveGoalCount)
    }

    @Test
    fun `the target is the first active goal in the backend's own order, not a ranking of ours`() {
        // list_goals returns updated_at DESC, id DESC. Home takes the first still-active row
        // and counts the rest -- it never sorts, scores or otherwise picks a favourite.
        val data = data(
            viewModel(
                goals = Result.success(
                    listOf(
                        goal(id = 7, title = "Most recently worked on"),
                        goal(id = 3, title = "Older goal"),
                    ),
                ),
            ),
        )

        assertEquals(7, data.activeGoal?.id)
        assertEquals(1, data.otherActiveGoalCount)
    }

    @Test
    fun `an archived goal is never presented as the active target`() {
        val data = data(
            viewModel(
                goals = Result.success(
                    listOf(
                        goal(id = 9, title = "Archived", status = "archived"),
                        goal(id = 4, title = "Still active", status = "active"),
                    ),
                ),
            ),
        )

        assertEquals("Still active", data.activeGoal?.title)
        assertEquals(0, data.otherActiveGoalCount, )
    }

    @Test
    fun `paused and completed goals are not the active target either`() {
        val data = data(
            viewModel(
                goals = Result.success(
                    listOf(goal(id = 1, status = "paused"), goal(id = 2, status = "completed")),
                ),
            ),
        )

        assertNull(data.activeGoal)
        assertEquals(SectionState.EMPTY, data.goalState)
    }

    // ======================================================================================
    // D-K. Application state
    // ======================================================================================

    @Test
    fun `a goal with no linked application reports null, which is not the same as not applied`() {
        val data = data(viewModel(goals = Result.success(listOf(goal(applicationStatus = null)))))

        assertNull(data.activeGoal?.applicationStatus)
    }

    @Test
    fun `every application stage reaches Home unchanged`() {
        // Phase 2E's vocabulary, carried through verbatim. Home reinterprets none of it: an
        // outcome means what the tracker says it means, and nothing here maps one to another.
        for (status in listOf("saved", "applied", "assessment", "interview", "offer", "rejected", "closed")) {
            val data = data(viewModel(goals = Result.success(listOf(goal(applicationStatus = status)))))

            assertEquals(status, data.activeGoal?.applicationStatus)
        }
    }

    // ======================================================================================
    // L-N. Evidence coverage
    // ======================================================================================

    @Test
    fun `evidence coverage null is empty, never zero`() {
        val data = data(viewModel(careerTwin = Result.success(twin(evidenceCoveragePercent = null))))

        assertNull(data.evidenceCoveragePercent)
        assertEquals(SectionState.EMPTY, data.evidenceState)
    }

    @Test
    fun `evidence coverage zero is a measurement and survives as zero`() {
        val data = data(viewModel(careerTwin = Result.success(twin(evidenceCoveragePercent = 0))))

        assertNotNull(data.evidenceCoveragePercent)
        assertEquals(0, data.evidenceCoveragePercent)
        assertEquals(SectionState.AVAILABLE, data.evidenceState)
    }

    @Test
    fun `evidence coverage is read from career twin, not recomputed`() {
        val data = data(viewModel(careerTwin = Result.success(twin(evidenceCoveragePercent = 73))))

        assertEquals(73, data.evidenceCoveragePercent)
    }

    @Test
    fun `a twin with no role has nothing for coverage to be coverage of`() {
        val data = data(
            viewModel(careerTwin = Result.success(twin(hasRole = false, evidenceCoveragePercent = 40))),
        )

        assertNull(data.evidenceCoveragePercent)
        assertEquals(SectionState.EMPTY, data.evidenceState)
    }

    // ======================================================================================
    // O-Q. Preparation
    // ======================================================================================

    @Test
    fun `preparation null is empty, never zero`() {
        val data = data(viewModel(paths = Result.success(listOf(pathSummary(preparationPercent = null, totalItems = 0)))))

        assertNull(data.preparationPercent)
        assertEquals(SectionState.EMPTY, data.preparationState)
    }

    @Test
    fun `preparation zero with a real checklist is a measured zero`() {
        val data = data(
            viewModel(
                paths = Result.success(
                    listOf(pathSummary(preparationPercent = 0, completedItems = 0, totalItems = 10)),
                ),
            ),
        )

        assertEquals(0, data.preparationPercent)
        assertEquals(SectionState.AVAILABLE, data.preparationState)
        assertEquals(10, data.preparationTotalItems)
    }

    @Test
    fun `preparation comes from the primary path's own percentage`() {
        val data = data(
            viewModel(
                paths = Result.success(
                    listOf(
                        pathSummary(preparationPercent = 20, isPrimary = false),
                        pathSummary(preparationPercent = 60, isPrimary = true),
                    ),
                ),
            ),
        )

        assertEquals(60, data.preparationPercent)
    }

    @Test
    fun `no career path at all leaves preparation empty`() {
        val data = data(viewModel(paths = Result.success(emptyList())))

        assertNull(data.preparationPercent)
        assertEquals(SectionState.EMPTY, data.preparationState)
    }

    // ======================================================================================
    // R-T. Performance
    // ======================================================================================

    @Test
    fun `performance null is empty, never zero`() {
        val data = data(viewModel(dashboard = Result.success(dashboard(performanceScore = null))))

        assertNull(data.performanceScore)
        assertEquals(SectionState.EMPTY, data.performanceState)
    }

    @Test
    fun `performance zero is a measured zero`() {
        val data = data(viewModel(dashboard = Result.success(dashboard(performanceScore = 0.0))))

        assertNotNull(data.performanceScore)
        assertEquals(0.0, data.performanceScore!!, 0.0001)
        assertEquals(SectionState.AVAILABLE, data.performanceState)
    }

    @Test
    fun `performance keeps the dashboard's own 0 to 1 scale`() {
        // Converting to a percentage is a rendering decision, made in the composable. Doing it
        // here would put a second representation of one number into the data layer.
        val data = data(viewModel(dashboard = Result.success(dashboard(performanceScore = 0.68))))

        assertEquals(0.68, data.performanceScore!!, 0.0001)
    }

    // ======================================================================================
    // U-V. Next Best Action
    // ======================================================================================

    @Test
    fun `the next action is the next best action service's, not a locally invented one`() {
        val data = data(viewModel(nextAction = Result.success(action(title = "Practice technical interview"))))

        assertEquals("Practice technical interview", data.nextBestAction?.title)
        assertEquals("interview", data.nextBestAction?.destination)
        assertEquals(SectionState.AVAILABLE, data.nextActionState)
    }

    @Test
    fun `no recommendation means no action shown, never a filler one`() {
        val data = data(viewModel(nextAction = Result.success(action(hasRecommendation = false))))

        assertNull(data.nextBestAction)
        assertEquals(SectionState.EMPTY, data.nextActionState)
    }

    // ======================================================================================
    // Y-Z. Partial and multiple failure
    // ======================================================================================

    @Test
    fun `one failing source degrades its own section and leaves the rest intact`() {
        val data = data(viewModel(dashboard = Result.failure(IOException("down"))))

        assertEquals(SectionState.UNAVAILABLE, data.performanceState)
        assertNull(data.performanceScore)
        // Everything else still rendered.
        assertEquals(SectionState.AVAILABLE, data.goalState)
        assertEquals(SectionState.AVAILABLE, data.evidenceState)
        assertEquals(SectionState.AVAILABLE, data.preparationState)
        assertEquals(SectionState.AVAILABLE, data.nextActionState)
    }

    @Test
    fun `a failed source is unavailable, never an empty result`() {
        // The distinction this whole design exists for: a dropped request must not read as
        // "we measured you and found nothing".
        val data = data(viewModel(careerTwin = Result.failure(IOException("down"))))

        assertEquals(SectionState.UNAVAILABLE, data.evidenceState)
        assertNull(data.evidenceCoveragePercent)
    }

    @Test
    fun `every source failing still renders Home rather than blanking it`() {
        val data = data(
            viewModel(
                goals = Result.failure(IOException("down")),
                paths = Result.failure(IOException("down")),
                dashboard = Result.failure(IOException("down")),
                careerTwin = Result.failure(IOException("down")),
                nextAction = Result.failure(IOException("down")),
            ),
        )

        assertEquals(SectionState.UNAVAILABLE, data.goalState)
        assertEquals(SectionState.UNAVAILABLE, data.evidenceState)
        assertEquals(SectionState.UNAVAILABLE, data.preparationState)
        assertEquals(SectionState.UNAVAILABLE, data.performanceState)
        assertEquals(SectionState.UNAVAILABLE, data.nextActionState)
        // And not one fabricated number among them.
        assertNull(data.evidenceCoveragePercent)
        assertNull(data.preparationPercent)
        assertNull(data.performanceScore)
        assertNull(data.activeGoal)
    }

    @Test
    fun `a failing goal source is unavailable, not an empty goal list`() {
        val data = data(viewModel(goals = Result.failure(IOException("down"))))

        assertEquals(SectionState.UNAVAILABLE, data.goalState)
        assertNull(data.activeGoal)
    }

    // ======================================================================================
    // AM. No fake values, and the greeting
    // ======================================================================================

    @Test
    fun `a brand new student gets no fabricated numbers anywhere`() {
        val data = data(
            viewModel(
                goals = Result.success(emptyList()),
                paths = Result.success(emptyList()),
                dashboard = Result.success(dashboard(performanceScore = null)),
                careerTwin = Result.success(twin(hasRole = false)),
                nextAction = Result.success(action(hasRecommendation = false)),
            ),
        )

        assertNull(data.activeGoal)
        assertNull(data.evidenceCoveragePercent)
        assertNull(data.preparationPercent)
        assertNull(data.performanceScore)
        assertNull(data.nextBestAction)
        assertNull(data.biggestGap)
    }

    @Test
    fun `greeting includes the student's first name when the profile has one, never a full name`() {
        val profile = FakeProfileRepository().apply {
            profileResult = Result.success(
                (profileResult.getOrThrow() as ProfileDto).copy(fullName = "Priya Raman"),
            )
        }
        val data = data(viewModel(profile = profile))

        assertTrue(data.greeting.contains("Priya"))
        assertFalse(data.greeting.contains("Raman"))
    }

    @Test
    fun `target role name prefers the custom target role over a catalog lookup`() {
        val profile = FakeProfileRepository().apply {
            profileResult = Result.success(
                (profileResult.getOrThrow() as ProfileDto).copy(customTargetRole = "ML Platform Engineer"),
            )
            rolesResult = Result.success(listOf(RoleDto(id = 1, name = "SDE", category = "Engineering")))
        }
        val data = data(viewModel(profile = profile))

        assertEquals("ML Platform Engineer", data.targetRoleName)
    }

    @Test
    fun `a failing profile leaves the greeting nameless instead of blanking Home`() {
        val profile = FakeProfileRepository().apply { profileResult = Result.failure(IOException("down")) }
        val data = data(viewModel(profile = profile))

        assertNull(data.targetRoleName)
        assertTrue(data.greeting.isNotEmpty())
        assertEquals(SectionState.AVAILABLE, data.goalState)
    }
}
