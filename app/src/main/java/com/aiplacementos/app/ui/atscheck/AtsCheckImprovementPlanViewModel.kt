package com.aiplacementos.app.ui.atscheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.AtsCheckReportDto
import com.aiplacementos.app.domain.repository.AtsCheckRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AtsCheckImprovementPlanViewModel(
    private val checkId: Int,
    private val atsCheckRepository: AtsCheckRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<AtsCheckReportDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<AtsCheckReportDto>> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val report = atsCheckRepository.getAtsCheck(checkId)
                _uiState.value = if (report.recommendations.isEmpty()) UiState.Empty else UiState.Success(report)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("load your improvement plan"))
            }
        }
    }
}
