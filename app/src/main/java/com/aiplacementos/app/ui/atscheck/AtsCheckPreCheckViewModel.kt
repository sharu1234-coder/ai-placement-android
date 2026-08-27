package com.aiplacementos.app.ui.atscheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.domain.repository.ResumeRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * UiState.Empty here means "no resume uploaded yet" -- a real, expected state (not an
 * error), rendered as an upload prompt rather than a broken card.
 */
class AtsCheckPreCheckViewModel(private val resumeRepository: ResumeRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<ResumeSummaryDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<ResumeSummaryDto>> = _uiState

    // No init{} auto-load -- the screen's LifecycleResumeEffect covers both the initial
    // load and refresh-on-return, matching JdAnalyzerViewModel's established convention.
    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val resume = resumeRepository.getLatestResume()
                _uiState.value = if (resume == null) UiState.Empty else UiState.Success(resume)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Couldn't load your resume.")
            }
        }
    }
}
