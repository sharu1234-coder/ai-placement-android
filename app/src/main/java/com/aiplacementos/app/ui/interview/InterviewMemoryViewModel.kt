package com.aiplacementos.app.ui.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.WeaknessRecordDto
import com.aiplacementos.app.domain.repository.WeaknessRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Deliberately tiny and independent -- like NextBestActionViewModel/TodaysMissionViewModel,
 * meant to be embedded (via WeaknessMemorySection) into the interview evaluation screen, not
 * opened as its own destination. A failure here renders nothing rather than disrupting the
 * evaluation the student is actually there to see -- no Error state exposed at all, since
 * this is a secondary, purely additive widget. */
sealed interface InterviewMemoryUiState {
    data object Loading : InterviewMemoryUiState
    data class Success(val records: List<WeaknessRecordDto>) : InterviewMemoryUiState
}

class InterviewMemoryViewModel(private val weaknessRepository: WeaknessRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<InterviewMemoryUiState>(InterviewMemoryUiState.Loading)
    val uiState: StateFlow<InterviewMemoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { weaknessRepository.listWeaknesses() }
                .onSuccess { _uiState.value = InterviewMemoryUiState.Success(it) }
                .onFailure { _uiState.value = InterviewMemoryUiState.Success(emptyList()) }
        }
    }
}
