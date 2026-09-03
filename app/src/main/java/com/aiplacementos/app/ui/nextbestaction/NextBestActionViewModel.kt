package com.aiplacementos.app.ui.nextbestaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.RecommendedActionDto
import com.aiplacementos.app.domain.repository.NextBestActionRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Deliberately tiny and independent -- Next Best Action is meant to be embedded (via
 * NextBestActionCard, ui/common/) into other screens, not opened as its own destination.
 * has_recommendation=false is still a real Success (e.g. "no target role set yet"), not an
 * Error -- the card itself renders that copy, no separate empty state needed here. */
sealed interface NextBestActionUiState {
    data object Loading : NextBestActionUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : NextBestActionUiState
    data class Success(val action: RecommendedActionDto) : NextBestActionUiState
}

class NextBestActionViewModel(private val nextBestActionRepository: NextBestActionRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<NextBestActionUiState>(NextBestActionUiState.Loading)
    val uiState: StateFlow<NextBestActionUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = NextBestActionUiState.Loading
            try {
                _uiState.value = NextBestActionUiState.Success(nextBestActionRepository.getNextBestAction())
            } catch (e: Exception) {
                _uiState.value = NextBestActionUiState.Error(
                    e.toUserMessage("load your next best action"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }
}
