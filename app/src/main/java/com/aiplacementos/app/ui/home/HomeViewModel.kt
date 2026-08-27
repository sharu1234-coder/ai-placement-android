package com.aiplacementos.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.alarm.AlarmScheduler
import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.data.remote.progressPercent
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.domain.repository.PlacementAlarmRepository
import com.aiplacementos.app.domain.repository.PlacementPathRepository
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
    val readinessPercent: Int?,
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
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val uiState: StateFlow<UiState<HomeData>> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            // All five sources are independent -- fetched concurrently instead of one after
            // another, since Home is the app's landing screen and every cold start otherwise
            // pays the sum of all latencies instead of the max.
            val (path, dashboard, alarms, roadmap, careerPath) = coroutineScope {
                val pathDeferred = async { runCatching { placementPathRepository.getCurrent() }.getOrNull() }
                val dashboardDeferred = async { runCatching { progressRepository.getDashboard() }.getOrNull() }
                val alarmsDeferred = async {
                    runCatching { placementAlarmRepository.getAllEnabledAlarms() }.getOrDefault(emptyList())
                }
                val roadmapDeferred = async { runCatching { roadmapRepository.getCurrentRoadmap(null) }.getOrNull() }
                val careerPathDeferred = async { runCatching { careerPathRepository.getCurrent() }.getOrNull() }
                Quintuple(
                    pathDeferred.await(),
                    dashboardDeferred.await(),
                    alarmsDeferred.await(),
                    roadmapDeferred.await(),
                    careerPathDeferred.await(),
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

            val streakDates = dashboard?.trend.orEmpty().mapNotNull { parseLocalDate(it.createdAt) }

            _uiState.value = UiState.Success(
                HomeData(
                    greeting = timeOfDayGreeting(),
                    readinessPercent = dashboard?.readinessScore?.let { (it * 100).toInt() },
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
                    recommendedForYou = dashboard?.topWeaknesses.orEmpty().take(3)
                        .map { "Your $it is weak -- practice it" },
                    recentActivity = recentActivity,
                    careerPath = careerPath,
                )
            )
        }
    }
}

private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
