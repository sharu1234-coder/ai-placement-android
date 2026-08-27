package com.aiplacementos.app.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.alarm.AlarmScheduler
import com.aiplacementos.app.database.entity.AlarmConfigEntity
import com.aiplacementos.app.database.entity.ChallengeEntity
import com.aiplacementos.app.domain.repository.PlacementAlarmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmListViewModel(
    private val repository: PlacementAlarmRepository,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    val alarms: StateFlow<List<AlarmConfigEntity>> = repository.observeAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeChallenges = MutableStateFlow<Map<Long, ChallengeEntity>>(emptyMap())
    val activeChallenges: StateFlow<Map<Long, ChallengeEntity>> = _activeChallenges.asStateFlow()

    init {
        refreshActiveChallenges()
    }

    fun refreshActiveChallenges() {
        viewModelScope.launch {
            _activeChallenges.value = repository.activeChallengesByAlarm()
        }
    }

    fun setEnabled(alarm: AlarmConfigEntity, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(alarm, enabled)
            if (enabled) {
                scheduler.scheduleNext(alarm.copy(enabled = true))
            } else {
                scheduler.cancel(alarm.id)
            }
        }
    }

    fun deleteAlarm(alarm: AlarmConfigEntity) {
        viewModelScope.launch {
            scheduler.cancel(alarm.id)
            repository.deleteAlarm(alarm)
        }
    }
}
