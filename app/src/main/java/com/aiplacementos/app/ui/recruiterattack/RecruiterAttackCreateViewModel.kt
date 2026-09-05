package com.aiplacementos.app.ui.recruiterattack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ProjectBuildListItemDto
import com.aiplacementos.app.domain.repository.ProjectBuilderRepository
import com.aiplacementos.app.domain.repository.RecruiterAttackRepository
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RecruiterAttackCreateUiState {
    data object Loading : RecruiterAttackCreateUiState
    data class Error(val message: String) : RecruiterAttackCreateUiState
    data class Ready(
        val completedBuilds: List<ProjectBuildListItemDto> = emptyList(),
        val submitting: Boolean = false,
        val submitError: String? = null,
    ) : RecruiterAttackCreateUiState
    data class Success(val sessionId: Int, val sourceLabel: String) : RecruiterAttackCreateUiState
}

class RecruiterAttackCreateViewModel(
    private val recruiterAttackRepository: RecruiterAttackRepository,
    private val projectBuilderRepository: ProjectBuilderRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RecruiterAttackCreateUiState>(RecruiterAttackCreateUiState.Loading)
    val uiState: StateFlow<RecruiterAttackCreateUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RecruiterAttackCreateUiState.Loading
            try {
                val completed = projectBuilderRepository.listBuilds().filter { it.status == "completed" }
                _uiState.value = RecruiterAttackCreateUiState.Ready(completedBuilds = completed)
            } catch (e: Exception) {
                _uiState.value = RecruiterAttackCreateUiState.Error(e.toUserMessage("load your completed projects"))
            }
        }
    }

    fun startFromProject(buildId: Int) {
        val current = _uiState.value
        if (current !is RecruiterAttackCreateUiState.Ready) return
        viewModelScope.launch {
            _uiState.value = current.copy(submitting = true, submitError = null)
            try {
                val session = recruiterAttackRepository.startSession("project_build", buildId, null)
                _uiState.value = RecruiterAttackCreateUiState.Success(session.id, session.sourceLabel)
            } catch (e: Exception) {
                _uiState.value = current.copy(submitting = false, submitError = e.toUserMessage("start this session"))
            }
        }
    }

    fun startFromResumeClaim(claimText: String) {
        val current = _uiState.value
        if (current !is RecruiterAttackCreateUiState.Ready) return
        val trimmed = claimText.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = current.copy(submitError = "Paste a real line from your resume first.")
            return
        }
        viewModelScope.launch {
            _uiState.value = current.copy(submitting = true, submitError = null)
            try {
                val session = recruiterAttackRepository.startSession("resume_claim", null, trimmed)
                _uiState.value = RecruiterAttackCreateUiState.Success(session.id, session.sourceLabel)
            } catch (e: Exception) {
                _uiState.value = current.copy(submitting = false, submitError = e.toUserMessage("start this session"))
            }
        }
    }
}
