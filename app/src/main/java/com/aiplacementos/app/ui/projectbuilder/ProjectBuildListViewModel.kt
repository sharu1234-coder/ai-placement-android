package com.aiplacementos.app.ui.projectbuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ProjectBuildListItemDto
import com.aiplacementos.app.domain.repository.ProjectBuilderRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProjectBuildListUiState {
    data object Loading : ProjectBuildListUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : ProjectBuildListUiState
    data class Success(val builds: List<ProjectBuildListItemDto>) : ProjectBuildListUiState
}

class ProjectBuildListViewModel(private val projectBuilderRepository: ProjectBuilderRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<ProjectBuildListUiState>(ProjectBuildListUiState.Loading)
    val uiState: StateFlow<ProjectBuildListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ProjectBuildListUiState.Loading
            try {
                _uiState.value = ProjectBuildListUiState.Success(projectBuilderRepository.listBuilds())
            } catch (e: Exception) {
                _uiState.value = ProjectBuildListUiState.Error(
                    e.toUserMessage("load your projects"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }
}
