package com.aiplacementos.app.ui.home

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.AtsCheckListItemDto
import com.aiplacementos.app.data.remote.CareerTwinDto
import com.aiplacementos.app.data.remote.NextBestActionDto
import com.aiplacementos.app.data.remote.PlacementPathDto
import com.aiplacementos.app.data.remote.ProgressDashboardDto
import com.aiplacementos.app.data.remote.ProgressTrendPointDto
import com.aiplacementos.app.data.remote.RoadmapDto
import com.aiplacementos.app.data.remote.RoadmapTaskDto
import com.aiplacementos.app.data.remote.SkillTwinItemDto
import com.aiplacementos.app.fakes.FakeAtsCheckRepository
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.fakes.FakeCareerTwinRepository
import com.aiplacementos.app.fakes.FakePlacementAlarmRepository
import com.aiplacementos.app.fakes.FakePlacementPathRepository
import com.aiplacementos.app.fakes.FakeProfileRepository
import com.aiplacementos.app.fakes.FakeProgressRepository
import com.aiplacementos.app.fakes.FakeRoadmapRepository
import com.aiplacementos.app.ui.common.UiState
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** An ISO-8601 UTC timestamp [days] days before now.
 *
 * "Recent Win" is bounded to the last week, so these tests have to express age relative to
 * today. A fixed literal like "2026-02-01T00:00:00Z" would pass on the day it was written and
 * then quietly change meaning every day afterwards until it fell out of the window entirely --
 * a test that rots into a false pass and then a confusing failure. */
private fun isoDaysAgo(days: Long): String =
    OffsetDateTime.now(ZoneOffset.UTC).minusDays(days).toString()

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

    /** hasRole=false by default -- matches the real repository's "no target role yet" shape and
     * keeps every pre-existing dashboard-only test asserting the old fallback behavior. */
    private fun noRoleCareerTwin() = CareerTwinDto(hasRole = false, roleSource = "none")

    private fun viewModel(
        path: PlacementPathDto? = path(),
        dashboard: ProgressDashboardDto? = dashboard(),
        roadmap: RoadmapDto? = null,
        careerTwin: CareerTwinDto = noRoleCareerTwin(),
    ): HomeViewModel {
        val placementPathRepository = FakePlacementPathRepository().apply { currentResult = { path } }
        val progressRepository = FakeProgressRepository().apply {
            dashboardResult = dashboard?.let { Result.success(it) } ?: Result.failure(java.io.IOException("down"))
        }
        val placementAlarmRepository = FakePlacementAlarmRepository()
        val roadmapRepository = FakeRoadmapRepository().apply { currentResult = { roadmap } }
        val careerPathRepository = FakeCareerPathRepository().apply { currentResult = { null } }
        val atsCheckRepository = FakeAtsCheckRepository()
        val careerTwinRepository = FakeCareerTwinRepository().apply { careerTwinResult = { careerTwin } }
        return HomeViewModel(
            placementPathRepository,
            progressRepository,
            placementAlarmRepository,
            roadmapRepository,
            careerPathRepository,
            atsCheckRepository,
            FakeProfileRepository(),
            careerTwinRepository,
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
    fun `recommended for you leads with real weak areas, then fills remaining slots from other signals`() {
        val data = (viewModel().uiState.value as UiState.Success<HomeData>).data

        assertEquals(
            listOf(
                "Your SQL JOINs is weak -- practice it",
                "Your Dynamic Programming is weak -- practice it",
                "Run an ATS check on your resume to see how it scores.",
            ),
            data.recommendedForYou,
        )
    }

    @Test
    fun `recommended for you surfaces test, interview, resume and target-role gaps when there are no weaknesses`() {
        val noWeaknessDashboard = dashboard().copy(completedAssessments = 0, completedInterviews = 0, topWeaknesses = emptyList())

        val data = (viewModel(dashboard = noWeaknessDashboard).uiState.value as UiState.Success<HomeData>).data

        assertEquals(
            listOf(
                "You haven't taken any tests yet -- start with a quick practice test.",
                "Try a mock interview to build your confidence.",
                "Run an ATS check on your resume to see how it scores.",
            ),
            data.recommendedForYou,
        )
    }

    @Test
    fun `recommended for you flags a low resume score once a check exists`() {
        val atsCheckRepository = FakeAtsCheckRepository().apply {
            listResult = Result.success(
                listOf(
                    AtsCheckListItemDto(
                        id = 1,
                        resumeId = 1,
                        resumeFilename = "resume.pdf",
                        overallScore = 40,
                        scoreCategory = "Needs Work",
                        createdAt = "2026-01-01T00:00:00Z",
                    ),
                ),
            )
        }
        val viewModel = HomeViewModel(
            FakePlacementPathRepository().apply { currentResult = { path() } },
            FakeProgressRepository().apply { dashboardResult = Result.success(dashboard()) },
            FakePlacementAlarmRepository(),
            FakeRoadmapRepository().apply { currentResult = { null } },
            FakeCareerPathRepository().apply { currentResult = { null } },
            atsCheckRepository,
            FakeProfileRepository(),
            FakeCareerTwinRepository().apply { careerTwinResult = { noRoleCareerTwin() } },
        )

        val data = (viewModel.uiState.value as UiState.Success<HomeData>).data

        assertTrue(data.recommendedForYou.contains("Your resume ATS score is low -- review the improvement plan."))
    }

    @Test
    fun `todays plan surfaces not-done tasks before completed ones`() {
        val today = LocalDate.now().toString()
        val roadmap = RoadmapDto(
            id = 1,
            jdId = null,
            durationDays = 30,
            status = "active",
            tasks = listOf(
                RoadmapTaskDto(1, 1, "practice", "SQL", "Joins", "desc", "done", today),
                RoadmapTaskDto(2, 1, "practice", "DSA", "Arrays", "desc", "not_started", today),
            ),
        )

        val data = (viewModel(roadmap = roadmap).uiState.value as UiState.Success<HomeData>).data

        assertEquals("Arrays", data.todaysPlan.first().title)
        assertFalse(data.todaysPlan.first().done)
    }

    @Test
    fun `resume health reflects the latest ats check, or null when none has been run`() {
        val atsCheckRepository = FakeAtsCheckRepository().apply {
            listResult = Result.success(
                listOf(
                    AtsCheckListItemDto(
                        id = 7,
                        resumeId = 1,
                        resumeFilename = "resume.pdf",
                        overallScore = 82,
                        scoreCategory = "Strong",
                        createdAt = "2026-01-01T00:00:00Z",
                    ),
                ),
            )
        }
        val placementPathRepository = FakePlacementPathRepository().apply { currentResult = { path() } }
        val progressRepository = FakeProgressRepository().apply { dashboardResult = Result.success(dashboard()) }
        val roadmapRepository = FakeRoadmapRepository().apply { currentResult = { null } }
        val careerPathRepository = FakeCareerPathRepository().apply { currentResult = { null } }
        val viewModel = HomeViewModel(
            placementPathRepository,
            progressRepository,
            FakePlacementAlarmRepository(),
            roadmapRepository,
            careerPathRepository,
            atsCheckRepository,
            FakeProfileRepository(),
            FakeCareerTwinRepository().apply { careerTwinResult = { noRoleCareerTwin() } },
        )

        val data = (viewModel.uiState.value as UiState.Success<HomeData>).data

        assertEquals(82, data.resumeHealth?.overallScore)
    }

    @Test
    fun `target role name prefers the custom target role over a catalog role lookup`() {
        val profileRepository = FakeProfileRepository().apply {
            profileResult = profileResult.getOrThrow().copy(customTargetRole = "Quant Researcher").let { Result.success(it) }
        }
        val viewModel = HomeViewModel(
            FakePlacementPathRepository().apply { currentResult = { path() } },
            FakeProgressRepository().apply { dashboardResult = Result.success(dashboard()) },
            FakePlacementAlarmRepository(),
            FakeRoadmapRepository().apply { currentResult = { null } },
            FakeCareerPathRepository().apply { currentResult = { null } },
            FakeAtsCheckRepository(),
            profileRepository,
            FakeCareerTwinRepository().apply { careerTwinResult = { noRoleCareerTwin() } },
        )

        val data = (viewModel.uiState.value as UiState.Success<HomeData>).data

        assertEquals("Quant Researcher", data.targetRoleName)
    }

    @Test
    fun `target role name is null when the profile has no target role set`() {
        val data = (viewModel().uiState.value as UiState.Success<HomeData>).data

        assertNull(data.targetRoleName)
    }

    @Test
    fun `biggest gap is the top weakness from real progress history, or null with no history`() {
        val withWeakness = (viewModel().uiState.value as UiState.Success<HomeData>).data
        assertEquals("SQL JOINs", withWeakness.biggestGap)

        val noWeaknessDashboard = dashboard().copy(topWeaknesses = emptyList())
        val withoutWeakness = (viewModel(dashboard = noWeaknessDashboard).uiState.value as UiState.Success<HomeData>).data
        assertNull(withoutWeakness.biggestGap)
    }

    @Test
    fun `a failing dashboard degrades gracefully instead of blanking the whole screen`() {
        val data = (viewModel(dashboard = null).uiState.value as UiState.Success<HomeData>).data

        assertNull(data.readinessPercent)
        assertEquals(0, data.currentStreakDays)
        // Weakness tips are dashboard-sourced and drop out, but resume/career-path tips are
        // independent signals that still populate -- Home never goes fully blank.
        assertEquals(
            listOf(
                "Run an ATS check on your resume to see how it scores.",
                "Pick a target role in Career Path for a focused prep plan.",
            ),
            data.recommendedForYou,
        )
        // The OTHER sources (placement path) still populate normally.
        assertEquals("SQL JOINs", data.nextBestActionTitle)
    }

    @Test
    fun `career twin readiness replaces the dashboard readiness once a target role is set`() {
        val twin = CareerTwinDto(
            hasRole = true,
            roleSource = "career_path",
            readinessPercent = 82,
            readinessTrendPercent = 6,
            isEstimate = true,
            biggestGapSkill = "System Design",
        )

        val data = (viewModel(careerTwin = twin).uiState.value as UiState.Success<HomeData>).data

        assertEquals(82, data.readinessPercent)
        assertEquals(6, data.readinessTrendPercent)
        assertTrue(data.readinessIsEstimate)
        // Career Twin's biggest gap takes priority over the dashboard's top weakness.
        assertEquals("System Design", data.biggestGap)
    }

    @Test
    fun `recent win surfaces the most recently verified skill and its confidence band, or null when none is verified yet`() {
        // Dates are relative to today on purpose: "Recent Win" is time-bounded now, so
        // hard-coded calendar dates would silently stop qualifying as the suite ages.
        val twin = CareerTwinDto(
            hasRole = true,
            roleSource = "career_path",
            skills = listOf(
                SkillTwinItemDto(skill = "SQL", weight = 1f, confidencePercent = 90, confidenceBand = "very_high", state = "verified", lastVerifiedAt = isoDaysAgo(5)),
                SkillTwinItemDto(skill = "Python", weight = 1f, confidencePercent = 90, confidenceBand = "very_high", state = "verified", lastVerifiedAt = isoDaysAgo(1)),
                SkillTwinItemDto(skill = "DSA", weight = 1f, confidencePercent = 40, confidenceBand = "moderate", state = "supported"),
            ),
        )

        val data = (viewModel(careerTwin = twin).uiState.value as UiState.Success<HomeData>).data

        assertEquals("Python", data.recentWinSkill)
        assertEquals("very_high", data.recentWinBand)
        assertEquals(2, data.verifiedSkillCount)

        val noEvidence = (viewModel().uiState.value as UiState.Success<HomeData>).data
        assertNull(noEvidence.recentWinSkill)
        assertNull(noEvidence.recentWinBand)
        assertNull(noEvidence.verifiedSkillCount)
    }

    @Test
    fun `a verification older than the recent-win window is not announced as something that just happened`() {
        // Regression: "just reached Verified status" used to be shown for the newest verified
        // skill regardless of age. The Evidence Engine only marks evidence stale after 90
        // days, so a student returning after a long break was congratulated for something
        // that had happened months earlier as though it were today's news. The skill is still
        // counted as verified -- it just is not a *recent* win.
        val twin = CareerTwinDto(
            hasRole = true,
            roleSource = "career_path",
            skills = listOf(
                SkillTwinItemDto(skill = "SQL", weight = 1f, confidencePercent = 90, confidenceBand = "very_high", state = "verified", lastVerifiedAt = isoDaysAgo(60)),
            ),
        )

        val data = (viewModel(careerTwin = twin).uiState.value as UiState.Success<HomeData>).data

        assertNull(data.recentWinSkill)
        assertNull(data.recentWinBand)
        assertEquals(1, data.verifiedSkillCount)
    }

    @Test
    fun `greeting includes the student's first name when the profile has one, never a full name`() {
        val profileRepository = FakeProfileRepository().apply {
            profileResult = profileResult.getOrThrow().copy(fullName = "Sharath Kumar").let { Result.success(it) }
        }
        val viewModel = HomeViewModel(
            FakePlacementPathRepository().apply { currentResult = { path() } },
            FakeProgressRepository().apply { dashboardResult = Result.success(dashboard()) },
            FakePlacementAlarmRepository(),
            FakeRoadmapRepository().apply { currentResult = { null } },
            FakeCareerPathRepository().apply { currentResult = { null } },
            FakeAtsCheckRepository(),
            profileRepository,
            FakeCareerTwinRepository().apply { careerTwinResult = { noRoleCareerTwin() } },
        )

        val data = (viewModel.uiState.value as UiState.Success<HomeData>).data

        assertTrue(data.greeting.contains("Sharath"))
        assertFalse(data.greeting.contains("Kumar"))
    }

    @Test
    fun `greeting has no name when the profile has none set`() {
        val data = (viewModel().uiState.value as UiState.Success<HomeData>).data

        assertFalse(data.greeting.contains(","))
    }

    @Test
    fun `biggest gap confidence band comes from career twin's own skill data, never the dashboard-only fallback`() {
        val twin = CareerTwinDto(
            hasRole = true,
            roleSource = "career_path",
            biggestGapSkill = "Kafka",
            skills = listOf(SkillTwinItemDto(skill = "Kafka", weight = 1f, confidencePercent = 0, confidenceBand = "none", state = "claimed")),
        )
        val withTwinGap = (viewModel(careerTwin = twin).uiState.value as UiState.Success<HomeData>).data
        assertEquals("Kafka", withTwinGap.biggestGap)
        assertEquals("none", withTwinGap.biggestGapBand)

        // Falls back to the dashboard's top weakness with no associated confidence to show.
        val dashboardOnly = (viewModel().uiState.value as UiState.Success<HomeData>).data
        assertEquals("SQL JOINs", dashboardOnly.biggestGap)
        assertNull(dashboardOnly.biggestGapBand)
    }

    @Test
    fun `next milestone is the smallest real threshold above current readiness, never a fabricated mission count`() {
        val twin = CareerTwinDto(hasRole = true, roleSource = "career_path", readinessPercent = 78)
        val data = (viewModel(careerTwin = twin).uiState.value as UiState.Success<HomeData>).data

        assertEquals(78, data.readinessPercent)
        assertEquals(85, data.nextMilestonePercent)
    }

    @Test
    fun `next milestone is null once the highest threshold is already cleared`() {
        val twin = CareerTwinDto(hasRole = true, roleSource = "career_path", readinessPercent = 97)
        val data = (viewModel(careerTwin = twin).uiState.value as UiState.Success<HomeData>).data

        assertNull(data.nextMilestonePercent)
    }
}
