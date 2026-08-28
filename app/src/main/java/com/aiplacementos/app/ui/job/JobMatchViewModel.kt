package com.aiplacementos.app.ui.job

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.JobMatchDto
import com.aiplacementos.app.domain.repository.JobRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.isNetworkError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class JobMatchViewModel(private val jdId: Int, private val jobRepository: JobRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<JobMatchDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<JobMatchDto>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                _uiState.value = UiState.Success(jobRepository.matchJob(jdId))
            } catch (e: HttpException) {
                _uiState.value = UiState.Error(
                    if (e.code() == 422) {
                        "Upload your resume first to see how you match this job."
                    } else {
                        "Couldn't load your match (${e.code()})."
                    }
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    "Couldn't reach the server. Check your connection.",
                    isNetworkError = e.isNetworkError(),
                )
            }
        }
    }
}
