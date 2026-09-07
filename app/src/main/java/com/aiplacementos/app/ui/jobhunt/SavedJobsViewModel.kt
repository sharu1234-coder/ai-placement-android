package com.aiplacementos.app.ui.jobhunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.SavedJobDto
import com.aiplacementos.app.domain.repository.JobHuntRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The application pipeline, in the order the tracker renders its columns. Mirrors the
 * backend's SAVED_JOB_STATUSES exactly -- a value the server would reject must never be
 * offerable in the UI.
 *
 * "dismissed" is deliberately absent from [pipelineStages]: a dismissed job is not a stage of
 * an application, and showing it as one would inflate a student's sense of progress.
 */
enum class ApplicationStage(val apiValue: String, val label: String) {
    Saved("saved", "Saved"),
    Applied("applied", "Applied"),
    Assessment("assessment", "Assessment"),
    Interview("interview", "Interview"),
    Offer("offer", "Offer"),
    Rejected("rejected", "Rejected"),
    Closed("closed", "Closed"),
    ;

    companion object {
        fun fromApiValue(value: String): ApplicationStage? = entries.find { it.apiValue == value }

        val pipelineStages: List<ApplicationStage>
            get() = listOf(Saved, Applied, Assessment, Interview, Offer)
    }
}

sealed interface SavedJobsUiState {
    data object Loading : SavedJobsUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : SavedJobsUiState
    data class Success(
        val jobs: List<SavedJobDto>,
        val updatingIds: Set<Int> = emptySet(),
        val actionError: String? = null,
    ) : SavedJobsUiState
}

class SavedJobsViewModel(
    private val jobHuntRepository: JobHuntRepository,
    /** When non-null, only these statuses are shown -- used by the tracker to exclude
     * dismissed jobs, while the Saved list shows everything. */
    private val statusFilter: List<String>? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SavedJobsUiState>(SavedJobsUiState.Loading)
    val uiState: StateFlow<SavedJobsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SavedJobsUiState.Loading
            try {
                _uiState.value = SavedJobsUiState.Success(jobHuntRepository.listSaved(statusFilter))
            } catch (e: Exception) {
                _uiState.value = SavedJobsUiState.Error(
                    e.toUserMessage("load your saved jobs"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }

    fun updateStage(savedJobId: Int, stage: ApplicationStage) {
        val current = _uiState.value as? SavedJobsUiState.Success ?: return
        if (savedJobId in current.updatingIds) return

        viewModelScope.launch {
            _uiState.value = current.copy(
                updatingIds = current.updatingIds + savedJobId,
                actionError = null,
            )
            try {
                val updated = jobHuntRepository.updateSaved(savedJobId, status = stage.apiValue)
                // Replace the single row from the server's response rather than reloading the
                // whole list: the server owns applied_at, and re-fetching one row keeps the
                // student's scroll position.
                updateSuccess { state ->
                    state.copy(jobs = state.jobs.map { if (it.id == savedJobId) updated else it })
                }
            } catch (e: Exception) {
                updateSuccess { it.copy(actionError = e.toUserMessage("update this application")) }
            } finally {
                updateSuccess { it.copy(updatingIds = it.updatingIds - savedJobId) }
            }
        }
    }

    fun saveNote(savedJobId: Int, note: String) {
        viewModelScope.launch {
            try {
                val updated = jobHuntRepository.updateSaved(savedJobId, notes = note)
                updateSuccess { state ->
                    state.copy(jobs = state.jobs.map { if (it.id == savedJobId) updated else it })
                }
            } catch (e: Exception) {
                updateSuccess { it.copy(actionError = e.toUserMessage("save your note")) }
            }
        }
    }

    fun remove(savedJobId: Int) {
        viewModelScope.launch {
            try {
                jobHuntRepository.deleteSaved(savedJobId)
                updateSuccess { state ->
                    state.copy(jobs = state.jobs.filterNot { it.id == savedJobId })
                }
            } catch (e: Exception) {
                updateSuccess { it.copy(actionError = e.toUserMessage("remove this job")) }
            }
        }
    }

    fun clearActionError() = updateSuccess { it.copy(actionError = null) }

    private fun updateSuccess(transform: (SavedJobsUiState.Success) -> SavedJobsUiState.Success) {
        val latest = _uiState.value as? SavedJobsUiState.Success ?: return
        _uiState.value = transform(latest)
    }
}
