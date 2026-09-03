package com.aiplacementos.app.ui.projectbuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ProjectBuildDto
import com.aiplacementos.app.domain.repository.ProjectBuilderRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProjectBuildDetailUiState {
    data object Loading : ProjectBuildDetailUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : ProjectBuildDetailUiState
    data class Success(val build: ProjectBuildDto) : ProjectBuildDetailUiState
}

class ProjectBuildDetailViewModel(
    private val buildId: Int,
    private val projectBuilderRepository: ProjectBuilderRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProjectBuildDetailUiState>(ProjectBuildDetailUiState.Loading)
    val uiState: StateFlow<ProjectBuildDetailUiState> = _uiState.asStateFlow()

    // A stage action failing (e.g. trying to start a locked stage) must not blank the whole
    // screen -- surfaced as a transient message instead, separate from the loaded build.
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ProjectBuildDetailUiState.Loading
            try {
                _uiState.value = ProjectBuildDetailUiState.Success(projectBuilderRepository.getBuild(buildId))
            } catch (e: Exception) {
                _uiState.value = ProjectBuildDetailUiState.Error(
                    e.toUserMessage("load this project"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }

    fun startStage(stageKey: String) {
        viewModelScope.launch {
            try {
                projectBuilderRepository.startStage(buildId, stageKey)
                _uiState.value = ProjectBuildDetailUiState.Success(projectBuilderRepository.getBuild(buildId))
            } catch (e: Exception) {
                _actionMessage.value = e.toUserMessage("start this stage")
            }
        }
    }

    fun completeStage(stageKey: String, notes: String) {
        viewModelScope.launch {
            try {
                projectBuilderRepository.completeStage(buildId, stageKey, notes.trim().ifBlank { null })
                _uiState.value = ProjectBuildDetailUiState.Success(projectBuilderRepository.getBuild(buildId))
            } catch (e: Exception) {
                _actionMessage.value = e.toUserMessage("complete this stage")
            }
        }
    }

    fun checkGithub(repoUrl: String) {
        val trimmed = repoUrl.trim()
        if (trimmed.isEmpty()) {
            _actionMessage.value = "Enter a GitHub repository URL first."
            return
        }
        viewModelScope.launch {
            try {
                projectBuilderRepository.checkGithub(buildId, trimmed)
                _uiState.value = ProjectBuildDetailUiState.Success(projectBuilderRepository.getBuild(buildId))
            } catch (e: Exception) {
                _actionMessage.value = e.toUserMessage("check this GitHub repository")
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
