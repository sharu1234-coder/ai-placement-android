package com.aiplacementos.app.ui.jobhunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.JobSearchProfileDto
import com.aiplacementos.app.domain.repository.JobHuntRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface JobHuntHomeUiState {
    data object Loading : JobHuntHomeUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : JobHuntHomeUiState

    /**
     * [profile] always loads, even when empty -- an empty Job Hunt profile is a real state the
     * setup screen exists for, not an error and not an Empty variant. The screen branches on
     * [JobSearchProfileDto.isReadyToSearch] to decide between the setup prompt and the
     * matches feed.
     */
    data class Success(val profile: JobSearchProfileDto) : JobHuntHomeUiState
}

class JobHuntHomeViewModel(private val jobHuntRepository: JobHuntRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<JobHuntHomeUiState>(JobHuntHomeUiState.Loading)
    val uiState: StateFlow<JobHuntHomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = JobHuntHomeUiState.Loading
            try {
                _uiState.value = JobHuntHomeUiState.Success(jobHuntRepository.getProfile())
            } catch (e: Exception) {
                _uiState.value = JobHuntHomeUiState.Error(
                    e.toUserMessage("load Job Hunt"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }
}
