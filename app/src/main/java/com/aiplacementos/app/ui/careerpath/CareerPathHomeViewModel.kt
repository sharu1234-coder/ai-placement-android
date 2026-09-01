package com.aiplacementos.app.ui.careerpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Same rationale as PlacementPathUiState: "no career path selected yet" is a real,
 * user-facing state distinct from a network error, not something UiState<T>'s generic
 * Empty/Error split models cleanly alongside a savedCount side-value. */
sealed interface CareerPathHomeUiState {
    data object Loading : CareerPathHomeUiState
    data class NoPath(val savedCount: Int) : CareerPathHomeUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : CareerPathHomeUiState
    data class Success(val current: CareerPathDto, val savedCount: Int) : CareerPathHomeUiState
}

class CareerPathHomeViewModel(private val careerPathRepository: CareerPathRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<CareerPathHomeUiState>(CareerPathHomeUiState.Loading)
    val uiState: StateFlow<CareerPathHomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = CareerPathHomeUiState.Loading
            try {
                val current = careerPathRepository.getCurrent()
                val savedCount = careerPathRepository.getAll().size
                _uiState.value = if (current != null) {
                    CareerPathHomeUiState.Success(current, savedCount)
                } else {
                    CareerPathHomeUiState.NoPath(savedCount)
                }
            } catch (e: Exception) {
                _uiState.value = CareerPathHomeUiState.Error(
                    e.toUserMessage("load your Career Path"),
                    // See PlacementPathViewModel.load()'s identical comment: a timeout gets
                    // its own message above, so it shouldn't also show the WifiOff icon.
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }
}
