package com.aiplacementos.app.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ProgressDashboardDto
import com.aiplacementos.app.domain.repository.ProgressRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProgressDashboardViewModel(private val progressRepository: ProgressRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<ProgressDashboardDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<ProgressDashboardDto>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                _uiState.value = UiState.Success(progressRepository.getDashboard())
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    e.toUserMessage("load your progress")
                )
            }
        }
    }
}
