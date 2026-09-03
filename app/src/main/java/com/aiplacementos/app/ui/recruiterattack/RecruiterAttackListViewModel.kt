package com.aiplacementos.app.ui.recruiterattack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.RecruiterAttackSessionListItemDto
import com.aiplacementos.app.domain.repository.RecruiterAttackRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RecruiterAttackListUiState {
    data object Loading : RecruiterAttackListUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : RecruiterAttackListUiState
    data class Success(val sessions: List<RecruiterAttackSessionListItemDto>) : RecruiterAttackListUiState
}

class RecruiterAttackListViewModel(private val recruiterAttackRepository: RecruiterAttackRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<RecruiterAttackListUiState>(RecruiterAttackListUiState.Loading)
    val uiState: StateFlow<RecruiterAttackListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RecruiterAttackListUiState.Loading
            try {
                _uiState.value = RecruiterAttackListUiState.Success(recruiterAttackRepository.listSessions())
            } catch (e: Exception) {
                _uiState.value = RecruiterAttackListUiState.Error(
                    e.toUserMessage("load your recruiter attack sessions"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }
}
