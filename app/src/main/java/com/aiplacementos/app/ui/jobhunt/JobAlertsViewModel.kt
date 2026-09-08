package com.aiplacementos.app.ui.jobhunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.JobAlertCreateDto
import com.aiplacementos.app.data.remote.JobAlertDto
import com.aiplacementos.app.data.remote.JobAlertHistoryDto
import com.aiplacementos.app.data.remote.JobAlertQuotaDto
import com.aiplacementos.app.data.remote.JobAlertUpdateDto
import com.aiplacementos.app.domain.repository.JobHuntRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface JobAlertsUiState {
    data object Loading : JobAlertsUiState

    data class Failed(val failure: JobSearchFailure) : JobAlertsUiState

    data class Success(
        val alerts: List<JobAlertDto>,
        val quota: JobAlertQuotaDto,
        val history: List<JobAlertHistoryDto> = emptyList(),
        val busyIds: Set<Int> = emptySet(),
        val actionError: String? = null,
        /** Set when the server refused for entitlement rather than for a fault, so the screen
         * can offer an upgrade instead of a retry. */
        val upgradeMessage: String? = null,
    ) : JobAlertsUiState {
        val isEmpty: Boolean get() = alerts.isEmpty()

        /** True when the student has room for another alert on their current plan. The server
         * decides for real; this only chooses which affordance to show. */
        val canCreate: Boolean get() = quota.hasRoom
    }
}

/**
 * Job alerts.
 *
 * Consent runs through everything here. An alert is created off, and the toggle is the act of
 * consenting -- there is no global switch that turns them all on, because a student who wanted
 * to hear about Dubai data roles did not thereby ask about everything else they ever searched.
 *
 * The client also never decides entitlement. `quota.hasRoom` picks which affordance to show;
 * whether an alert can actually be created is the server's answer, and a refusal comes back as
 * a message this screen shows verbatim rather than as a disabled button with no explanation.
 */
class JobAlertsViewModel(private val jobHuntRepository: JobHuntRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<JobAlertsUiState>(JobAlertsUiState.Loading)
    val uiState: StateFlow<JobAlertsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = JobAlertsUiState.Loading
            try {
                val alerts = jobHuntRepository.listAlerts()
                val quota = jobHuntRepository.getAlertQuota()
                // History degrades independently: a student whose alerts load must not be
                // blocked from managing them because the history query failed.
                val history = runCatching { jobHuntRepository.getAlertHistory() }
                    .getOrDefault(emptyList())
                _uiState.value = JobAlertsUiState.Success(alerts, quota, history)
            } catch (e: Exception) {
                _uiState.value = JobAlertsUiState.Failed(e.toJobSearchFailure())
            }
        }
    }

    fun retry() = load()

    fun create(
        name: String,
        savedSearchId: Int? = null,
        frequency: String = "daily",
        dayOfWeek: Int? = null,
        hourLocal: Int = 9,
        quietHoursStart: Int? = null,
        quietHoursEnd: Int? = null,
        isEnabled: Boolean = false,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                jobHuntRepository.createAlert(
                    JobAlertCreateDto(
                        name = trimmed,
                        savedSearchId = savedSearchId,
                        frequency = frequency,
                        dayOfWeek = dayOfWeek,
                        hourLocal = hourLocal,
                        quietHoursStart = quietHoursStart,
                        quietHoursEnd = quietHoursEnd,
                        isEnabled = isEnabled,
                    ),
                )
                load()
            } catch (e: Exception) {
                val failure = e.toJobSearchFailure()
                updateSuccess {
                    if (failure is JobSearchFailure.PremiumRequired) {
                        // An entitlement ceiling, not a fault. Routed to the upgrade prompt.
                        it.copy(upgradeMessage = failure.message)
                    } else {
                        it.copy(actionError = failure.message)
                    }
                }
            }
        }
    }

    /** The consent toggle. Optimism is deliberately avoided here: a notification switch that
     * shows "on" before the server agreed is the one control that must never lie. */
    fun setEnabled(alertId: Int, enabled: Boolean) {
        val current = _uiState.value as? JobAlertsUiState.Success ?: return
        if (alertId in current.busyIds) return

        viewModelScope.launch {
            _uiState.value = current.copy(busyIds = current.busyIds + alertId, actionError = null)
            try {
                val updated = jobHuntRepository.updateAlert(
                    alertId,
                    JobAlertUpdateDto(isEnabled = enabled),
                )
                updateSuccess { state ->
                    state.copy(
                        alerts = state.alerts.map { if (it.id == alertId) updated else it },
                        busyIds = state.busyIds - alertId,
                    )
                }
            } catch (e: Exception) {
                updateSuccess {
                    it.copy(
                        busyIds = it.busyIds - alertId,
                        actionError = e.toJobSearchFailure().message,
                    )
                }
            }
        }
    }

    fun delete(alertId: Int) {
        val current = _uiState.value as? JobAlertsUiState.Success ?: return
        if (alertId in current.busyIds) return

        viewModelScope.launch {
            _uiState.value = current.copy(busyIds = current.busyIds + alertId, actionError = null)
            try {
                jobHuntRepository.deleteAlert(alertId)
                updateSuccess { state ->
                    state.copy(
                        alerts = state.alerts.filterNot { it.id == alertId },
                        busyIds = state.busyIds - alertId,
                        // The quota freed up, so the create affordance comes back without a
                        // full reload.
                        quota = state.quota.copy(used = (state.quota.used - 1).coerceAtLeast(0)),
                    )
                }
            } catch (e: Exception) {
                updateSuccess {
                    it.copy(
                        busyIds = it.busyIds - alertId,
                        actionError = e.toJobSearchFailure().message,
                    )
                }
            }
        }
    }

    fun clearMessages() = updateSuccess { it.copy(actionError = null, upgradeMessage = null) }

    private fun updateSuccess(transform: (JobAlertsUiState.Success) -> JobAlertsUiState.Success) {
        val current = _uiState.value as? JobAlertsUiState.Success ?: return
        _uiState.value = transform(current)
    }
}

// -- Display helpers, pure so they are testable without a Compose runtime --------------------

private val WEEKDAYS = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
)

/**
 * "Daily · 9:00 AM" or "Weekly · Monday".
 *
 * Says exactly what was configured. A vaguer "Regularly" would be easier to write and would
 * leave the student unable to tell whether the alert is doing what they asked.
 */
fun alertScheduleLabel(alert: JobAlertDto): String {
    val time = formatHour(alert.hourLocal)
    return when (alert.frequency) {
        "weekly" -> {
            val day = alert.dayOfWeek?.let { WEEKDAYS.getOrNull(it) }
            if (day != null) "Weekly · $day · $time" else "Weekly · $time"
        }
        "daily" -> "Daily · $time"
        // An unrecognised frequency from a newer backend is shown as its raw value rather
        // than mislabelled as daily, which would tell the student something untrue about how
        // often we will contact them.
        else -> "${alert.frequency} · $time"
    }
}

fun formatHour(hour: Int): String {
    val safe = hour.coerceIn(0, 23)
    val suffix = if (safe < 12) "AM" else "PM"
    val display = when {
        safe == 0 -> 12
        safe > 12 -> safe - 12
        else -> safe
    }
    return "$display:00 $suffix"
}

fun quietHoursLabel(alert: JobAlertDto): String? {
    val start = alert.quietHoursStart ?: return null
    val end = alert.quietHoursEnd ?: return null
    return "Quiet ${formatHour(start)} – ${formatHour(end)}"
}

/**
 * What the last run did, or null when it has never run.
 *
 * The three failure statuses are worded so a student is never left to conclude that a quiet
 * alert means an empty job market. "We couldn't check" and "nothing new" are different
 * sentences because they are different facts.
 */
fun alertLastRunLabel(alert: JobAlertDto): String? = when {
    alert.lastRunStatus == null -> null
    alert.lastRunStatus == "success" -> {
        val count = alert.lastNewJobCount ?: 0
        if (count == 1) "1 new job last time" else "$count new jobs last time"
    }
    alert.lastRunStatus == "no_new_jobs" -> "Nothing new last time"
    alert.lastRunStatus == "provider_error" -> "We couldn't check last time"
    alert.lastRunStatus == "not_configured" -> "Add a role or resume so this can run"
    alert.lastRunStatus == "skipped_quiet_hours" -> "Skipped — it fell inside your quiet hours"
    else -> null
}
