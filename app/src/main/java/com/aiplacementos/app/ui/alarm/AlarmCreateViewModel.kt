package com.aiplacementos.app.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.alarm.AlarmScheduler
import com.aiplacementos.app.database.entity.AlarmConfigEntity
import com.aiplacementos.app.domain.repository.PlacementAlarmRepository
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Eligibility is checked once up front (not per keystroke/toggle) -- the create screen has
 * no topic/skill/count/difficulty inputs to react to in the first place, per the fixed
 * product requirement (time + repeat only). Skipped entirely in edit mode -- the alarm
 * already exists and has already cleared this bar once; re-blocking a schedule/metadata
 * edit on it would be a regression, not a safeguard. */
sealed interface EligibilityState {
    data object Checking : EligibilityState
    data object Eligible : EligibilityState
    data object NotEligible : EligibilityState
    data class Error(val message: String) : EligibilityState
}

class AlarmCreateViewModel(
    private val repository: PlacementAlarmRepository,
    private val scheduler: AlarmScheduler,
    /** 0 means create mode -- same sentinel convention used for jdId elsewhere in the nav
     * graph (see Screen.kt). A non-zero id is fetched once up front and edited in place. */
    private val alarmId: Long = 0L,
) : ViewModel() {

    val isEditing: Boolean = alarmId != 0L

    private val _existingAlarm = MutableStateFlow<AlarmConfigEntity?>(null)
    val existingAlarm: StateFlow<AlarmConfigEntity?> = _existingAlarm.asStateFlow()

    private val _loadingExisting = MutableStateFlow(isEditing)
    val loadingExisting: StateFlow<Boolean> = _loadingExisting.asStateFlow()

    private val _eligibility = MutableStateFlow<EligibilityState>(
        if (isEditing) EligibilityState.Eligible else EligibilityState.Checking
    )
    val eligibility: StateFlow<EligibilityState> = _eligibility.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        if (isEditing) {
            viewModelScope.launch {
                _existingAlarm.value = repository.getAlarm(alarmId)
                _loadingExisting.value = false
            }
        } else {
            checkEligibility()
        }
    }

    fun checkEligibility() {
        viewModelScope.launch {
            _eligibility.value = EligibilityState.Checking
            _eligibility.value = try {
                if (repository.checkEligibility()) EligibilityState.Eligible else EligibilityState.NotEligible
            } catch (e: Exception) {
                EligibilityState.Error(e.toUserMessage("check your resume"))
            }
        }
    }

    fun save(hour: Int, minute: Int, repeatDays: Set<Int>, title: String, type: String, notes: String) {
        viewModelScope.launch {
            val repeatDaysCsv = repeatDays.sorted().joinToString(",")
            val trimmedTitle = title.trim().ifBlank { null }
            val trimmedNotes = notes.trim().ifBlank { null }

            val alarm = _existingAlarm.value?.let { current ->
                val updated = current.copy(
                    hour = hour,
                    minute = minute,
                    repeatDays = repeatDaysCsv,
                    title = trimmedTitle,
                    type = type,
                    notes = trimmedNotes,
                )
                repository.updateAlarm(updated)
                updated
            } ?: run {
                val id = repository.createAlarm(hour, minute, repeatDaysCsv, trimmedTitle, type, trimmedNotes)
                AlarmConfigEntity(
                    id = id,
                    hour = hour,
                    minute = minute,
                    repeatDays = repeatDaysCsv,
                    title = trimmedTitle,
                    type = type,
                    notes = trimmedNotes,
                )
            }

            // Existing schedule/enabled-state cancels + re-arms cleanly through the same
            // call whether this was a create or an edit (scheduleNext no-ops if disabled).
            scheduler.scheduleNext(alarm)
            _saved.value = true
        }
    }
}
