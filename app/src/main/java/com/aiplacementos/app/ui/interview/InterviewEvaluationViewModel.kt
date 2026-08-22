package com.aiplacementos.app.ui.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.InterviewEvaluationDto
import com.aiplacementos.app.domain.repository.InterviewRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InterviewEvaluationViewModel(
    private val interviewId: Int,
    private val interviewRepository: InterviewRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<InterviewEvaluationDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<InterviewEvaluationDto>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                _uiState.value = UiState.Success(interviewRepository.getEvaluation(interviewId))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    "Couldn't load your evaluation. Check your connection and try again."
                )
            }
        }
    }
}
