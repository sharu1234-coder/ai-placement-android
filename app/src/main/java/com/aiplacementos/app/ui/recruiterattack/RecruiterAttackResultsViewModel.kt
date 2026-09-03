package com.aiplacementos.app.ui.recruiterattack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.RecruiterAttackResultsDto
import com.aiplacementos.app.domain.repository.RecruiterAttackRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RecruiterAttackResultsUiState {
    data object Loading : RecruiterAttackResultsUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : RecruiterAttackResultsUiState
    data class Success(val results: RecruiterAttackResultsDto) : RecruiterAttackResultsUiState
}

class RecruiterAttackResultsViewModel(
    private val sessionId: Int,
    private val recruiterAttackRepository: RecruiterAttackRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RecruiterAttackResultsUiState>(RecruiterAttackResultsUiState.Loading)
    val uiState: StateFlow<RecruiterAttackResultsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RecruiterAttackResultsUiState.Loading
            try {
                _uiState.value = RecruiterAttackResultsUiState.Success(recruiterAttackRepository.getResults(sessionId))
            } catch (e: Exception) {
                _uiState.value = RecruiterAttackResultsUiState.Error(
                    e.toUserMessage("load your results"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }
}
