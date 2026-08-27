package com.aiplacementos.app.ui.home

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.NextBestActionDto
import com.aiplacementos.app.data.remote.PlacementPathDto
import com.aiplacementos.app.data.remote.ProgressDashboardDto
import com.aiplacementos.app.data.remote.ProgressTrendPointDto
import com.aiplacementos.app.data.remote.RoadmapDto
import com.aiplacementos.app.data.remote.RoadmapTaskDto
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.fakes.FakePlacementAlarmRepository
import com.aiplacementos.app.fakes.FakePlacementPathRepository
import com.aiplacementos.app.fakes.FakeProgressRepository
import com.aiplacementos.app.fakes.FakeRoadmapRepository
import com.aiplacementos.app.ui.common.UiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun path() = PlacementPathDto(
        id = 1,
        status = "active",
        createdAt = "2026-01-01T00:00:00Z",
        completedTopics = 5,
        totalTopics = 9,
        stages = emptyList(),
        nextBestAction = NextBestActionDto(topicId = 1, title = "SQL JOINs", reason = "next up"),
    )

    private fun dashboard(trend: List<ProgressTrendPointDto> = emptyList()) = ProgressDashboardDto(
        readinessScore = 0.68,
        completedAssessments = 24,
        completedInterviews = 3,
        roadmapTasksDone = 10,
        roadmapTasksTotal = 20,
        topWeaknesses = listOf("SQL JOINs", "Dynamic Programming"),
        trend = trend,
    )

    private fun viewModel(
        path: PlacementPathDto? = path(),
        dashboard: ProgressDashboardDto? = dashboard(),
        roadmap: RoadmapDto? = null,
    ): HomeViewModel {
        val placementPathRepository = FakePlacementPathRepository().apply { currentResult = { path } }
        val progressRepository = FakeProgressRepository().apply {
            dashboardResult = dashboard?.let { Result.success(it) } ?: Result.failure(java.io.IOException("down"))
        }
        val placementAlarmRepository = FakePlacementAlarmRepository()
        val roadmapRepository = FakeRoadmapRepository().apply { currentResult = { roadmap } }
        val careerPathRepository = FakeCareerPathRepository().apply { currentResult = { null } }
        return HomeViewModel(
            placementPathRepository,
            progressRepository,
            placementAlarmRepository,
            roadmapRepository,
            careerPathRepository,
        )
    }

    @Test
    fun `computes readiness percent and sub-metrics from real repository data`() {
        val viewModel = viewModel()

        val data = (viewModel.uiState.value as UiState.Success<HomeData>).data
        assertEquals(68, data.readinessPercent)
        assertTrue(data.subMetrics.any { it.label == "Roadmap" && it.value == "56%" })
        assertTrue(data.subMetrics.any { it.label == "Tests Completed" && it.value == "24" })
        assertEquals("SQL JOINs", data.nextBestActionTitle)
    }

    @Test
    fun `todays plan only includes tasks due today`() {
        val today = LocalDate.now().toString()
        val roadmap = RoadmapDto(
            id = 1,
            jdId = null,
            durationDays = 30,
            status = "active",
            tasks = listOf(
                RoadmapTaskDto(1, 1, "practice", "DSA", "Arrays", "desc", "not_started", today),
                RoadmapTaskDto(2, 1, "practice", "SQL", "Joins", "desc", "done", today),
                RoadmapTaskDto(3, 2, "practice", "Aptitude", "Reasoning", "desc", "not_started", "2020-01-01"),
            ),
        )

        val data = (viewModel(roadmap = roadmap).uiState.value as UiState.Success<HomeData>).data

        assertEquals(2, data.todaysPlan.size)
        assertTrue(data.todaysPlan.any { it.title == "Arrays" && !it.done })
        assertTrue(data.todaysPlan.any { it.title == "Joins" && it.done })
    }

    @Test
    fun `streak is computed from real trend timestamps, not fabricated`() {
        val today = LocalDate.now()
        val trend = listOf(
            ProgressTrendPointDto(createdAt = "${today}T10:00:00Z", kind = "assessment", label = "SQL Test", score = 0.8),
            ProgressTrendPointDto(createdAt = "${today.minusDays(1)}T10:00:00Z", kind = "interview", label = "Mock", score = 0.7),
        )

        val data = (viewModel(dashboard = dashboard(trend)).uiState.value as UiState.Success<HomeData>).data

        assertEquals(2, data.currentStreakDays)
    }

    @Test
    fun `recommended for you is templated from real weak areas`() {
        val data = (viewModel().uiState.value as UiState.Success<HomeData>).data

        assertEquals(
            listOf("Your SQL JOINs is weak -- practice it", "Your Dynamic Programming is weak -- practice it"),
            data.recommendedForYou,
        )
    }

    @Test
    fun `a failing dashboard degrades gracefully instead of blanking the whole screen`() {
        val data = (viewModel(dashboard = null).uiState.value as UiState.Success<HomeData>).data

        assertNull(data.readinessPercent)
        assertEquals(emptyList<String>(), data.recommendedForYou)
        assertEquals(0, data.currentStreakDays)
        // The OTHER sources (placement path) still populate normally.
        assertEquals("SQL JOINs", data.nextBestActionTitle)
    }
}
