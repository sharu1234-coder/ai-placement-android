package com.aiplacementos.app.ui.tests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.AssessmentDto
import com.aiplacementos.app.domain.repository.AssessmentRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TestListViewModel(
    private val blueprintId: Int,
    private val assessmentRepository: AssessmentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<AssessmentDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<AssessmentDto>> = _uiState.asStateFlow()

    init {
        start()
    }

    fun start() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                _uiState.value = UiState.Success(assessmentRepository.startAssessment(blueprintId))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    e.toUserMessage("start the assessment")
                )
            }
        }
    }
}
