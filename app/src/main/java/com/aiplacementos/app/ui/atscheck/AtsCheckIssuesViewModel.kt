package com.aiplacementos.app.ui.atscheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.AtsIssueDto
import com.aiplacementos.app.domain.repository.AtsCheckRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AtsCheckIssuesViewModel(
    private val checkId: Int,
    private val atsCheckRepository: AtsCheckRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<AtsIssueDto>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<AtsIssueDto>>> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val issues = atsCheckRepository.getAtsCheck(checkId).issues
                _uiState.value = if (issues.isEmpty()) UiState.Empty else UiState.Success(issues)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("load issues"))
            }
        }
    }
}
