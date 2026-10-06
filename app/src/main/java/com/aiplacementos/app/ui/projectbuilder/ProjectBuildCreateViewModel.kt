package com.aiplacementos.app.ui.projectbuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.OperationKeyHolder
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

    // The most expensive create in the app -- a build plus ten generated stages -- so a
    // replayed request is a duplicate project and a second full generation pass.
    //
    // Keyed by the title and stack summary actually sent, because those are exactly what the
    // server hashes into its request fingerprint. A single slot was wrong here: a create that
    // failed, a corrected title, and a second attempt sent the first attempt's key with a
    // different fingerprint, and the server answered that with 409 -- correctly, and uselessly
    // for the student, who only wanted to fix a typo.
    private val createKeys = OperationKeyHolder()

    fun create(title: String, stackSummary: String) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            _uiState.value = ProjectBuildCreateUiState.Error("Give your project a title first.")
            return
        }
        viewModelScope.launch {
            _uiState.value = ProjectBuildCreateUiState.Submitting
            try {
                val sentStack = stackSummary.trim().ifBlank { null }
                val identity = "$trimmedTitle\u0000${sentStack.orEmpty()}"
                val build = projectBuilderRepository.createBuild(
                    trimmedTitle, sentStack, createKeys.keyFor(identity),
                )
                createKeys.consume(identity)
                _uiState.value = ProjectBuildCreateUiState.Success(build.id)
            } catch (e: Exception) {
                _uiState.value = ProjectBuildCreateUiState.Error(e.toUserMessage("start this project"))
            }
        }
    }
}
