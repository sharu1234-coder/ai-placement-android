package com.aiplacementos.app.ui.atscheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.AtsCheckListItemDto
import com.aiplacementos.app.domain.repository.AtsCheckRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AtsCheckHistoryViewModel(private val atsCheckRepository: AtsCheckRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<AtsCheckListItemDto>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<AtsCheckListItemDto>>> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                // Server returns most-recent-first; the chart wants oldest-first for a
                // left-to-right progression, so the screen reverses just for that use.
                val checks = atsCheckRepository.listAtsChecks()
                _uiState.value = if (checks.isEmpty()) UiState.Empty else UiState.Success(checks)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Couldn't load your ATS check history.")
            }
        }
    }
}
