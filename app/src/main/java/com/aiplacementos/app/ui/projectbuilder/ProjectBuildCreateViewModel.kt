package com.aiplacementos.app.ui.projectbuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.domain.repository.ProjectBuilderRepository
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProjectBuildCreateUiState {
    data object Idle : ProjectBuildCreateUiState
    data object Submitting : ProjectBuildCreateUiState
    data class Error(val message: String) : ProjectBuildCreateUiState
    data class Success(val buildId: Int) : ProjectBuildCreateUiState
}

class ProjectBuildCreateViewModel(private val projectBuilderRepository: ProjectBuilderRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<ProjectBuildCreateUiState>(ProjectBuildCreateUiState.Idle)
    val uiState: StateFlow<ProjectBuildCreateUiState> = _uiState.asStateFlow()

    fun create(title: String, stackSummary: String) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            _uiState.value = ProjectBuildCreateUiState.Error("Give your project a title first.")
            return
        }
        viewModelScope.launch {
            _uiState.value = ProjectBuildCreateUiState.Submitting
            try {
                val build = projectBuilderRepository.createBuild(trimmedTitle, stackSummary.trim().ifBlank { null })
                _uiState.value = ProjectBuildCreateUiState.Success(build.id)
            } catch (e: Exception) {
                _uiState.value = ProjectBuildCreateUiState.Error(e.toUserMessage("start this project"))
            }
        }
    }
}
