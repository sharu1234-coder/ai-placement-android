package com.aiplacementos.app.ui.careerpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.domain.repository.ResumeRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResumeConnectionData(val path: CareerPathDto, val latestResumeId: Int?)

class CareerPathResumeConnectionViewModel(
    private val pathId: Int,
    private val careerPathRepository: CareerPathRepository,
    private val resumeRepository: ResumeRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<ResumeConnectionData>>(UiState.Loading)
    val uiState: StateFlow<UiState<ResumeConnectionData>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val path = careerPathRepository.getPath(pathId)
                // Best-effort -- if the resume lookup fails, the "Improve Resume" deep-link
                // just isn't shown, it never blocks the rest of this screen from loading.
                val latestResumeId = runCatching { resumeRepository.getLatestResume()?.id }.getOrNull()
                _uiState.value = UiState.Success(ResumeConnectionData(path, latestResumeId))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("load your resume connection"))
            }
        }
    }
}
