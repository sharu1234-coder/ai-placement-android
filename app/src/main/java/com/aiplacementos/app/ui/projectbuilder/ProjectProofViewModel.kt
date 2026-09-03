package com.aiplacementos.app.ui.projectbuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ProjectProofDto
import com.aiplacementos.app.domain.repository.ProjectBuilderRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProjectProofUiState {
    data object Loading : ProjectProofUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : ProjectProofUiState
    data class Success(val proof: ProjectProofDto) : ProjectProofUiState
}

class ProjectProofViewModel(
    private val buildId: Int,
    private val projectBuilderRepository: ProjectBuilderRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProjectProofUiState>(ProjectProofUiState.Loading)
    val uiState: StateFlow<ProjectProofUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ProjectProofUiState.Loading
            try {
                _uiState.value = ProjectProofUiState.Success(projectBuilderRepository.getProof(buildId))
            } catch (e: Exception) {
                _uiState.value = ProjectProofUiState.Error(
                    e.toUserMessage("load your project proof"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }
}
