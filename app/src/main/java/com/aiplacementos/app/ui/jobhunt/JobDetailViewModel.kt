package com.aiplacementos.app.ui.jobhunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.JobResultDto
import com.aiplacementos.app.domain.repository.JobHuntRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface JobDetailUiState {
    data object Loading : JobDetailUiState
    data class Failed(val failure: JobSearchFailure) : JobDetailUiState
    data class Success(
        val job: JobResultDto,
        val savedJobId: Int? = null,
        val isSaving: Boolean = false,
        val actionError: String? = null,
    ) : JobDetailUiState
}

class JobDetailViewModel(
    private val jobHuntRepository: JobHuntRepository,
    private val listingId: Int,
) : ViewModel() {
    private val _uiState = MutableStateFlow<JobDetailUiState>(JobDetailUiState.Loading)
    val uiState: StateFlow<JobDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = JobDetailUiState.Loading
            try {
                val job = jobHuntRepository.getJob(listingId)
                _uiState.value = JobDetailUiState.Success(job = job, savedJobId = job.savedJobId)
            } catch (e: Exception) {
                _uiState.value = JobDetailUiState.Failed(e.toJobSearchFailure())
            }
        }
    }

    fun toggleSave() {
        val current = _uiState.value as? JobDetailUiState.Success ?: return
        if (current.isSaving) return

        viewModelScope.launch {
            _uiState.value = current.copy(isSaving = true, actionError = null)
            try {
                val existing = current.savedJobId
                if (existing != null) {
                    jobHuntRepository.deleteSaved(existing)
                    updateSuccess { it.copy(savedJobId = null, isSaving = false) }
                } else {
                    val saved = jobHuntRepository.saveJob(listingId)
                    updateSuccess { it.copy(savedJobId = saved.id, isSaving = false) }
                }
            } catch (e: Exception) {
                updateSuccess {
                    it.copy(isSaving = false, actionError = e.toJobSearchFailure().message)
                }
            }
        }
    }

    /**
     * Called when the student opens the external listing. The status moves to "applied" only
     * because they took a real action -- this is not a claim that they submitted an
     * application, and the tracker labels it as a step the student can correct.
     */
    fun markApplied() {
        val current = _uiState.value as? JobDetailUiState.Success ?: return
        viewModelScope.launch {
            try {
                val savedId = current.savedJobId ?: jobHuntRepository.saveJob(listingId).id
                jobHuntRepository.updateSaved(savedId, status = "applied")
                updateSuccess { it.copy(savedJobId = savedId) }
            } catch (e: Exception) {
                updateSuccess { it.copy(actionError = e.toJobSearchFailure().message) }
            }
        }
    }

    fun clearActionError() = updateSuccess { it.copy(actionError = null) }

    private fun updateSuccess(transform: (JobDetailUiState.Success) -> JobDetailUiState.Success) {
        val latest = _uiState.value as? JobDetailUiState.Success ?: return
        _uiState.value = transform(latest)
    }
}
