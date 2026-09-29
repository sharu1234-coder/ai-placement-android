package com.aiplacementos.app.ui.home

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.CareerGoalListItemDto
import com.aiplacementos.app.data.remote.CareerPathSummaryDto
import com.aiplacementos.app.data.remote.CareerTwinDto
import com.aiplacementos.app.data.remote.ProgressDashboardDto
import com.aiplacementos.app.data.remote.RecommendedActionDto
import com.aiplacementos.app.fakes.FakeCareerGoalRepository
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.fakes.FakeCareerTwinRepository
import com.aiplacementos.app.fakes.FakeNextBestActionRepository
import com.aiplacementos.app.fakes.FakeProfileRepository
import com.aiplacementos.app.fakes.FakeProgressRepository
import com.aiplacementos.app.ui.common.UiState
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Phase 3.1: Home stops showing yesterday's answer.
 *
 * Home's ViewModel outlives its composable. MainScreen swaps bottom tabs with a `when` over
 * `selectedRoute`, and opening a Career Goal pushes a destination that pops back to the same
 * back-stack entry -- so the ViewModel store is the same one throughout. An `init{}` load
 * therefore ran exactly once per session, and every later visit to Home showed the target,
 * stage and signals as they were the first time, no matter what the student changed in
 * between. The state most likely to be stale was the state they had just edited.
 *
 * The fix is the screen's `LifecycleResumeEffect` calling `load()`, which is this codebase's
 * existing convention. The tests below cover the three things that convention gets wrong if
 * implemented naively on a screen that fans out to six requests:
 *
 *     stacking      rapid resumes must not multiply six requests by however many resumes
 *     flicker       a refresh must not blank a correct screen back to a skeleton
 *     data loss     a refresh that fails must not replace good data with "unavailable"
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeRefreshTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun goal(
        id: Int = 1,
        title: String = "Data Engineer",
        applicationStatus: String? = null,
        preparationState: String = "in_progress",
    ) = CareerGoalListItemDto(
        id = id,
        title = title,
        company = "Contoso",
        location = "Hyderabad",
        status = "active",
        isAnalyzed = true,
        preparationState = preparationState,
        applicationStatus = applicationStatus,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-03-01T00:00:00Z",
    )

    private fun pathSummary(preparationPercent: Int? = 60, completedItems: Int = 6) =
        CareerPathSummaryDto(
            id = 1,
            roleFamilyKey = "data_engineer",
            displayRoleName = "Data Engineer",
            isPrimary = true,
            preparationPercent = preparationPercent,
            completedItems = completedItems,
            totalItems = 10,
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

    private fun twin(evidenceCoveragePercent: Int? = 73) =
        CareerTwinDto(hasRole = true, roleSource = "career_path", evidenceCoveragePercent = evidenceCoveragePercent)

    private fun action() = RecommendedActionDto(
        hasRecommendation = true,
        title = "Practice technical interview",
        actionType = "practice",
        destination = "interview",
    )

    /**
     * A ViewModel whose sources are re-read on every load, so a test can change what the
     * "server" holds between loads -- which is the whole point: the fakes are lambdas, and a
     * refresh must call them again rather than reuse the first answer.
     */
    private class Harness {
        var goals: () -> List<CareerGoalListItemDto> = { emptyList() }
        var paths: () -> List<CareerPathSummaryDto> = { emptyList() }
        var dashboardResult: () -> ProgressDashboardDto = { throw IOException("not set") }
        var twinResult: () -> CareerTwinDto = { throw IOException("not set") }
        var actionResult: () -> RecommendedActionDto = { throw IOException("not set") }
        val goalCalls = AtomicInteger(0)

        fun build(): HomeViewModel {
            // FakeProgressRepository holds a Result rather than a lambda, and a refresh test
            // needs the value re-read on every load -- hence the tiny delegating repository.
            return HomeViewModel(
                progressRepository = object : com.aiplacementos.app.domain.repository.ProgressRepository {
                    override suspend fun getDashboard(): ProgressDashboardDto = dashboardResult()
                },
                careerPathRepository = FakeCareerPathRepository().apply { allResult = { paths() } },
                profileRepository = FakeProfileRepository(),
                careerTwinRepository = FakeCareerTwinRepository().apply { careerTwinResult = { twinResult() } },
                careerGoalRepository = FakeCareerGoalRepository().apply {
                    listGoalsResult = {
                        goalCalls.incrementAndGet()
                        goals()
                    }
                },
                nextBestActionRepository = FakeNextBestActionRepository().apply { result = { actionResult() } },
            )
        }
    }

    private fun harness(): Harness = Harness().apply {
        goals = { listOf(goal()) }
        paths = { listOf(pathSummary()) }
        dashboardResult = { dashboard() }
        twinResult = { twin() }
        actionResult = { action() }
    }

    private fun data(viewModel: HomeViewModel): HomeData =
        (viewModel.uiState.value as UiState.Success).data

    // ======================================================================================
    // A-B. Loading, and loading again on resume
    // ======================================================================================

    @Test
    fun `home loads on the first resume`() {
        val h = harness()
        val viewModel = h.build()

        // Before the screen resumes there is nothing -- the ViewModel has no init{} load.
        assertTrue(viewModel.uiState.value is UiState.Loading)

        viewModel.load()

        assertEquals("Data Engineer", data(viewModel).activeGoal?.title)
    }

    @Test
    fun `resuming again re-reads the sources rather than replaying the first answer`() {
        val h = harness()
        val viewModel = h.build()

        viewModel.load()
        viewModel.load()

        assertEquals(2, h.goalCalls.get())
    }

    // ======================================================================================
    // C-J. State changed elsewhere shows up on return
    // ======================================================================================

    @Test
    fun `a goal edited elsewhere appears on the next resume`() {
        val h = harness()
        val viewModel = h.build()
        viewModel.load()
        assertEquals("Data Engineer", data(viewModel).activeGoal?.title)

        h.goals = { listOf(goal(title = "Senior Data Engineer")) }
        viewModel.load()

        assertEquals("Senior Data Engineer", data(viewModel).activeGoal?.title)
    }

    @Test
    fun `an application stage changed elsewhere appears on the next resume`() {
        val h = harness()
        h.goals = { listOf(goal(applicationStatus = "applied")) }
        val viewModel = h.build()
        viewModel.load()
        assertEquals("applied", data(viewModel).activeGoal?.applicationStatus)

        h.goals = { listOf(goal(applicationStatus = "interview")) }
        viewModel.load()

        assertEquals("interview", data(viewModel).activeGoal?.applicationStatus)
    }

    @Test
    fun `linking an application appears on the next resume`() {
        val h = harness()
        val viewModel = h.build()
        viewModel.load()
        assertNull("starts unlinked", data(viewModel).activeGoal?.applicationStatus)

        h.goals = { listOf(goal(applicationStatus = "saved")) }
        viewModel.load()

        assertEquals("saved", data(viewModel).activeGoal?.applicationStatus)
    }

    @Test
    fun `preparation progress changed elsewhere appears on the next resume`() {
        val h = harness()
        val viewModel = h.build()
        viewModel.load()
        assertEquals(60, data(viewModel).preparationPercent)

        h.paths = { listOf(pathSummary(preparationPercent = 80, completedItems = 8)) }
        viewModel.load()

        assertEquals(80, data(viewModel).preparationPercent)
        assertEquals(8, data(viewModel).preparationCompletedItems)
    }

    @Test
    fun `performance changed by a completed interview appears on the next resume`() {
        val h = harness()
        h.dashboardResult = { dashboard(performanceScore = null) }
        val viewModel = h.build()
        viewModel.load()
        assertNull(data(viewModel).performanceScore)
        assertEquals(SectionState.EMPTY, data(viewModel).performanceState)

        h.dashboardResult = { dashboard(performanceScore = 0.72) }
        viewModel.load()

        assertEquals(0.72, data(viewModel).performanceScore!!, 0.0001)
        assertEquals(SectionState.AVAILABLE, data(viewModel).performanceState)
    }

    @Test
    fun `evidence coverage changed elsewhere appears on the next resume`() {
        val h = harness()
        val viewModel = h.build()
        viewModel.load()
        assertEquals(73, data(viewModel).evidenceCoveragePercent)

        h.twinResult = { twin(evidenceCoveragePercent = 81) }
        viewModel.load()

        assertEquals(81, data(viewModel).evidenceCoveragePercent)
    }

    @Test
    fun `a goal archived elsewhere stops being the target on the next resume`() {
        val h = harness()
        val viewModel = h.build()
        viewModel.load()
        assertNotNull(data(viewModel).activeGoal)

        h.goals = { emptyList() }
        viewModel.load()

        assertNull(data(viewModel).activeGoal)
        assertEquals(SectionState.EMPTY, data(viewModel).goalState)
    }

    // ======================================================================================
    // K-L. Concurrency: no request storm, no losing race
    // ======================================================================================

    @Test
    fun `rapid repeated resumes do not stack concurrent loads`() = runTest {
        // A real dispatcher that only runs work when told, so the loads genuinely overlap
        // rather than each completing before the next begins.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val h = harness()
            val viewModel = h.build()

            repeat(5) { viewModel.load() }
            testScheduler.advanceUntilIdle()

            // Four superseded, one survives. Without cancellation this would be five loads of
            // six requests each -- thirty in-flight calls for five taps on a tab.
            assertEquals(1, h.goalCalls.get())
            assertEquals("Data Engineer", data(viewModel).activeGoal?.title)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a superseded load cannot overwrite the newer one's result`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val h = harness()
            h.goals = { listOf(goal(title = "Stale")) }
            val viewModel = h.build()

            viewModel.load()          // begins with "Stale"
            h.goals = { listOf(goal(title = "Fresh")) }
            viewModel.load()          // supersedes it
            testScheduler.advanceUntilIdle()

            assertEquals("Fresh", data(viewModel).activeGoal?.title)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ======================================================================================
    // M-N. A failed refresh must not destroy a correct screen
    // ======================================================================================

    @Test
    fun `a totally failed refresh keeps the data already on screen`() {
        val h = harness()
        val viewModel = h.build()
        viewModel.load()
        assertEquals("Data Engineer", data(viewModel).activeGoal?.title)
        assertEquals(73, data(viewModel).evidenceCoveragePercent)

        // Connection drops entirely, then the student returns to Home.
        h.goals = { throw IOException("offline") }
        h.paths = { throw IOException("offline") }
        h.dashboardResult = { throw IOException("offline") }
        h.twinResult = { throw IOException("offline") }
        h.actionResult = { throw IOException("offline") }
        viewModel.load()

        // Still the last good Home, rather than six "couldn't be loaded" rows over a screen
        // that was correct a second ago.
        assertEquals("Data Engineer", data(viewModel).activeGoal?.title)
        assertEquals(73, data(viewModel).evidenceCoveragePercent)
    }

    @Test
    fun `a partial refresh failure still publishes what it learned`() {
        val h = harness()
        val viewModel = h.build()
        viewModel.load()

        // Only Career Twin fails; the goal genuinely moved on.
        h.twinResult = { throw IOException("offline") }
        h.goals = { listOf(goal(applicationStatus = "offer")) }
        viewModel.load()

        assertEquals("offer", data(viewModel).activeGoal?.applicationStatus)
        assertEquals(SectionState.UNAVAILABLE, data(viewModel).evidenceState)
        assertNull("and never a 0 standing in for the failure", data(viewModel).evidenceCoveragePercent)
    }

    @Test
    fun `a first load that fails everywhere still renders Home, not a blank screen`() {
        val h = harness()
        h.goals = { throw IOException("offline") }
        h.paths = { throw IOException("offline") }
        h.dashboardResult = { throw IOException("offline") }
        h.twinResult = { throw IOException("offline") }
        h.actionResult = { throw IOException("offline") }
        val viewModel = h.build()

        viewModel.load()

        val data = data(viewModel)
        assertEquals(SectionState.UNAVAILABLE, data.goalState)
        assertEquals(SectionState.UNAVAILABLE, data.evidenceState)
        assertNull(data.evidenceCoveragePercent)
        assertNull(data.preparationPercent)
        assertNull(data.performanceScore)
    }

    // ======================================================================================
    // The flicker rule
    // ======================================================================================

    @Test
    fun `a refresh never drops back to Loading over data already on screen`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val h = harness()
            val viewModel = h.build()
            viewModel.load()
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value is UiState.Success)

            viewModel.load()

            // Mid-refresh, before anything has come back: the previous Home is still there.
            // Blanking it would flash a skeleton on every single return from a Career Goal.
            assertTrue(
                "a refresh must not blank a correct screen",
                viewModel.uiState.value is UiState.Success,
            )
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value is UiState.Success)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ======================================================================================
    // O. No new API surface
    // ======================================================================================

    @Test
    fun `a resume costs exactly one goal read, not one per section`() {
        val h = harness()
        val viewModel = h.build()

        viewModel.load()

        // One call to /career-goals serves both the target card and the application signal.
        // If this ever becomes 2, Home has started fetching the same thing twice.
        assertEquals(1, h.goalCalls.get())
    }
}
