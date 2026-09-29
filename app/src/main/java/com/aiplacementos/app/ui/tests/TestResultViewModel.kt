package com.aiplacementos.app.ui.tests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.AssessmentResultDto
import com.aiplacementos.app.domain.repository.AssessmentRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TestResultViewModel(
    private val assessmentId: Int,
    private val assessmentRepository: AssessmentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<AssessmentResultDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<AssessmentResultDto>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                _uiState.value = UiState.Success(assessmentRepository.getResult(assessmentId))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    e.toUserMessage("load your results")
                )
            }
        }
    }
}
