package com.aiplacementos.app.ui.mission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.MissionDto
import com.aiplacementos.app.domain.repository.MissionRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Deliberately tiny and independent -- like NextBestActionViewModel, meant to be embedded
 * (via TodaysMissionCard, ui/mission/) into Home, not opened as its own destination. start()
 * and complete() are no-ops while a mission isn't loaded yet, and both re-render straight
 * from the repository's response -- the backend's own idempotent start/complete endpoints are
 * the single source of truth for status, never guessed client-side. */
sealed interface TodaysMissionUiState {
    data object Loading : TodaysMissionUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : TodaysMissionUiState
    data class Success(val mission: MissionDto) : TodaysMissionUiState
}

class TodaysMissionViewModel(private val missionRepository: MissionRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<TodaysMissionUiState>(TodaysMissionUiState.Loading)
    val uiState: StateFlow<TodaysMissionUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = TodaysMissionUiState.Loading
            runCatching { missionRepository.getToday() }
                .onSuccess { _uiState.value = TodaysMissionUiState.Success(it) }
                .onFailure { setError(it, "load today's mission") }
        }
    }

    fun start() {
        val mission = (_uiState.value as? TodaysMissionUiState.Success)?.mission ?: return
        viewModelScope.launch {
            runCatching { missionRepository.start(mission.id) }
                .onSuccess { _uiState.value = TodaysMissionUiState.Success(it) }
                .onFailure { setError(it, "start this mission") }
        }
    }

    fun complete() {
        val mission = (_uiState.value as? TodaysMissionUiState.Success)?.mission ?: return
        viewModelScope.launch {
            runCatching { missionRepository.complete(mission.id) }
                .onSuccess { _uiState.value = TodaysMissionUiState.Success(it) }
                .onFailure { setError(it, "complete this mission") }
        }
    }

    private fun setError(e: Throwable, action: String) {
        _uiState.value = TodaysMissionUiState.Error(
            e.toUserMessage(action),
            isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
        )
    }
}
