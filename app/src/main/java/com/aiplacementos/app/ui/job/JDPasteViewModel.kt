package com.aiplacementos.app.ui.job

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.JobDescriptionParseResponseDto
import com.aiplacementos.app.domain.repository.JobRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class JDPasteViewModel(private val jobRepository: JobRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<JobDescriptionParseResponseDto>>(UiState.Empty)
    val uiState: StateFlow<UiState<JobDescriptionParseResponseDto>> = _uiState.asStateFlow()

    fun parse(rawText: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                _uiState.value = UiState.Success(jobRepository.parseJobDescription(rawText))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    "Couldn't analyze that job description. Check your connection and try again."
                )
            }
        }
    }
}
