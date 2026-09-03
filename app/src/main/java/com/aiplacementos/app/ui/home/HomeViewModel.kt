package com.aiplacementos.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.alarm.AlarmScheduler
import com.aiplacementos.app.data.remote.AtsCheckListItemDto
import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.data.remote.ProgressDashboardDto
import com.aiplacementos.app.data.remote.progressPercent
import com.aiplacementos.app.domain.repository.AtsCheckRepository
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.domain.repository.CareerTwinRepository
import com.aiplacementos.app.domain.repository.PlacementAlarmRepository
import com.aiplacementos.app.domain.repository.PlacementPathRepository
import com.aiplacementos.app.domain.repository.ProfileRepository
import com.aiplacementos.app.domain.repository.ProgressRepository
import com.aiplacementos.app.domain.repository.RoadmapRepository
import com.aiplacementos.app.domain.streak.currentStreakDays
import com.aiplacementos.app.ui.common.UiState
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RecentActivityItem(val label: String, val scorePercent: Int, val isInterview: Boolean)

data class ReadinessSubMetric(val label: String, val value: String)

data class TodayPlanItem(val id: Int, val title: String, val taskType: String, val done: Boolean)

data class HomeData(
    val greeting: String,
    // Career Twin's readiness number when a target role/twin is available (the one
    // authoritative "how ready am I" figure home shows -- see the Phase 12 decision that this
    // REPLACES the old Placement Path-derived readiness, never a second number alongside it);
    // falls back to the Placement Path dashboard's score only when no Career Twin data exists
    // yet, so Home never regresses to blank for a student who hasn't picked a target role.
    val readinessPercent: Int?,
    val readinessTrendPercent: Int?,
    val readinessIsEstimate: Boolean,
    val subMetrics: List<ReadinessSubMetric>,
    val todaysPlan: List<TodayPlanItem>,
    val nextBestActionTitle: String?,
    val upcomingAlarmLabel: String?,
    val currentStreakDays: Int,
    val recommendedForYou: List<String>,
    val recentActivity: List<RecentActivityItem>,
    // Deliberately separate from the readinessPercent/subMetrics above -- Career Path is an
    // independent feature with its own progress model (see ui/careerpath/ package docs),
    // never merged into the Placement Path-derived readiness card's data.
    val careerPath: CareerPathDto?,
    // Latest resume ATS check, if any -- null means "no check run yet" (not an error), which
    // the UI renders as a promo prompting the student to run their first check.
    val resumeHealth: AtsCheckListItemDto?,
    // Same resolution MainScreenViewModel's drawer header already uses (customTargetRole,
    // falling back to a lookup in the role catalog) -- null means the student hasn't set one.
    val targetRoleName: String?,
    // The single weakest area, preferring Career Twin's evidence-backed biggest gap over the
    // older progress-history weakness list -- falls back to the latter only when Career Twin
    // has no data (e.g. no target role set yet), same degrade-gracefully rule as readiness.
    val biggestGap: String?,
    // The most recently evidence-verified skill (by Evidence Engine's own last-verified
    // timestamp), or null when nothing has reached "verified" yet -- never a fabricated
    // congratulatory message, only a real state transition already recorded server-side.
    val recentWinSkill: String?,
    // Count of skills currently in the "verified" state on the Career Twin. Null (not zero)
    // means "no Career Twin data at all" so the UI can hide the card entirely; zero is a real,
    // honest count that's still worth showing once a target role is set.
    val verifiedSkillCount: Int?,
)

private fun timeOfDayGreeting(): String = when (LocalTime.now().hour) {
    in 0..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

private fun formatAlarmLabel(triggerMillis: Long): String =
    SimpleDateFormat("EEE, h:mm a", Locale.getDefault()).format(Date(triggerMillis))

private fun parseLocalDate(isoDateTime: String): LocalDate? = try {
    OffsetDateTime.parse(isoDateTime).toLocalDate()
} catch (e: DateTimeParseException) {
    null
}

/**
 * Rule-based recommendations from real signals only -- weak skills, whether the student has
 * started practicing at all, resume ATS health, and whether a target role is even set.
 * Weak-skill tips take priority (most specific/actionable); the rest fill remaining slots
 * in a fixed order. Capped at 3 so Home never turns into a wall of nudges.
 */
private fun buildRecommendations(
    dashboard: ProgressDashboardDto?,
    resumeHealth: AtsCheckListItemDto?,
    careerPath: CareerPathDto?,
): List<String> {
    val weaknessTips = dashboard?.topWeaknesses.orEmpty().map { "Your $it is weak -- practice it" }
    val otherTips = listOfNotNull(
        "You haven't taken any tests yet -- start with a quick practice test."
            .takeIf { dashboard != null && dashboard.completedAssessments == 0 },
        "Try a mock interview to build your confidence."
            .takeIf { dashboard != null && dashboard.completedInterviews == 0 },
        when {
            resumeHealth == null -> "Run an ATS check on your resume to see how it scores."
            resumeHealth.overallScore < 70 -> "Your resume ATS score is low -- review the improvement plan."
            else -> null
        },
        "Pick a target role in Career Path for a focused prep plan.".takeIf { careerPath == null },
    )
    return (weaknessTips + otherTips).take(3)
}

/**
 * Real dashboard data, reusing existing repositories only. Each section is fetched
 * independently with runCatching so one failing piece degrades that one section gracefully
 * rather than blanking the whole Home screen -- a dashboard should never look dead just
 * because one card's data isn't available yet. No fabricated numbers anywhere: every field
 * traces back to a real, already-persisted value (see the redesign plan's Phase C2/C3).
 */
class HomeViewModel(
    private val placementPathRepository: PlacementPathRepository,
    private val progressRepository: ProgressRepository,
    private val placementAlarmRepository: PlacementAlarmRepository,
    private val roadmapRepository: RoadmapRepository,
    private val careerPathRepository: CareerPathRepository,
    private val atsCheckRepository: AtsCheckRepository,
    private val profileRepository: ProfileRepository,
    private val careerTwinRepository: CareerTwinRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val uiState: StateFlow<UiState<HomeData>> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            // All eight sources are independent -- fetched concurrently instead of one after
            // another, since Home is the app's landing screen and every cold start otherwise
            // pays the sum of all latencies instead of the max.
            val (path, dashboard, alarms, roadmap, careerPath, resumeHealth, targetRoleName, careerTwin) = coroutineScope {
                val pathDeferred = async { runCatching { placementPathRepository.getCurrent() }.getOrNull() }
                val dashboardDeferred = async { runCatching { progressRepository.getDashboard() }.getOrNull() }
                val alarmsDeferred = async {
                    runCatching { placementAlarmRepository.getAllEnabledAlarms() }.getOrDefault(emptyList())
                }
                val roadmapDeferred = async { runCatching { roadmapRepository.getCurrentRoadmap(null) }.getOrNull() }
                val careerPathDeferred = async { runCatching { careerPathRepository.getCurrent() }.getOrNull() }
                val resumeHealthDeferred = async {
                    runCatching { atsCheckRepository.listAtsChecks().firstOrNull() }.getOrNull()
                }
                val targetRoleNameDeferred = async {
                    runCatching {
                        val profile = profileRepository.getMyProfile()
                        profile.customTargetRole
                            ?: profileRepository.getRoles().find { it.id == profile.targetRoleId }?.name
                    }.getOrNull()
                }
                val careerTwinDeferred = async {
                    runCatching { careerTwinRepository.getCareerTwin() }.getOrNull()?.takeIf { it.hasRole }
                }
                Octuple(
                    pathDeferred.await(),
                    dashboardDeferred.await(),
                    alarmsDeferred.await(),
                    roadmapDeferred.await(),
                    careerPathDeferred.await(),
                    resumeHealthDeferred.await(),
                    targetRoleNameDeferred.await(),
                    careerTwinDeferred.await(),
                )
            }

            val recentActivity = dashboard?.trend.orEmpty().takeLast(3).reversed().map {
                RecentActivityItem(
                    label = it.label,
                    scorePercent = (it.score * 100).toInt(),
                    isInterview = it.kind == "interview",
                )
            }

            val upcomingAlarmLabel = alarms
                .minOfOrNull { AlarmScheduler.nextTriggerMillis(it.hour, it.minute, it.repeatDays) }
                ?.let(::formatAlarmLabel)

            val todayIso = LocalDate.now().toString()
            val todaysPlan = roadmap?.tasks.orEmpty()
                .filter { it.dueDate.startsWith(todayIso) }
                .map { TodayPlanItem(id = it.id, title = it.title, taskType = it.taskType, done = it.status == "done") }
                // Prioritized: what's still outstanding today surfaces above what's already done.
                .sortedBy { it.done }

            val streakDates = dashboard?.trend.orEmpty().mapNotNull { parseLocalDate(it.createdAt) }

            // Most recently verified skill, by Evidence Engine's own last-verified timestamp --
            // ISO-8601 timestamps sort correctly as strings, same trick relied on elsewhere in
            // this file for streak dates. Never a fabricated "well done"; the transition itself
            // already happened server-side, this just surfaces the newest one.
            val recentWinSkill = careerTwin?.skills
                ?.filter { it.state == "verified" && it.lastVerifiedAt != null }
                ?.maxByOrNull { it.lastVerifiedAt!! }
                ?.skill

            _uiState.value = UiState.Success(
                HomeData(
                    greeting = timeOfDayGreeting(),
                    readinessPercent = careerTwin?.readinessPercent ?: dashboard?.readinessScore?.let { (it * 100).toInt() },
                    readinessTrendPercent = careerTwin?.readinessTrendPercent,
                    readinessIsEstimate = careerTwin?.isEstimate ?: false,
                    subMetrics = listOfNotNull(
                        path?.let { ReadinessSubMetric("Roadmap", "${it.progressPercent()}%") },
                        dashboard?.let { ReadinessSubMetric("Tests Completed", "${it.completedAssessments}") },
                        path?.let { ReadinessSubMetric("Topics Completed", "${it.completedTopics}") },
                        dashboard?.let { ReadinessSubMetric("Interviews Done", "${it.completedInterviews}") },
                    ),
                    todaysPlan = todaysPlan,
                    nextBestActionTitle = path?.nextBestAction?.title,
                    upcomingAlarmLabel = upcomingAlarmLabel,
                    currentStreakDays = currentStreakDays(streakDates),
                    recommendedForYou = buildRecommendations(dashboard, resumeHealth, careerPath),
                    recentActivity = recentActivity,
                    careerPath = careerPath,
                    resumeHealth = resumeHealth,
                    targetRoleName = targetRoleName,
                    biggestGap = careerTwin?.biggestGapSkill ?: dashboard?.topWeaknesses?.firstOrNull(),
                    recentWinSkill = recentWinSkill,
                    verifiedSkillCount = careerTwin?.skills?.count { it.state == "verified" },
                )
            )
        }
    }
}

private data class Octuple<A, B, C, D, E, F, G, H>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
    val seventh: G,
    val eighth: H,
)
