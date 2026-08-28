package com.aiplacementos.app.ui.job

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.AssessmentBlueprintDto
import com.aiplacementos.app.domain.repository.AssessmentRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.isNetworkError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PrepareMeSummaryViewModel(
    private val jdId: Int,
    private val assessmentRepository: AssessmentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<AssessmentBlueprintDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<AssessmentBlueprintDto>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                _uiState.value = UiState.Success(
                    assessmentRepository.createBlueprint(roleId = null, jdId = jdId)
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    "Couldn't build your preparation plan. Check your connection and try again.",
                    isNetworkError = e.isNetworkError(),
                )
            }
        }
    }
}
