package com.aiplacementos.app.ui.careertwin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CareerTwinDto
import com.aiplacementos.app.domain.repository.CareerTwinRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** has_role=false is a real, user-facing state ("select a target role first"), not an error --
 * same rationale as PlacementPathUiState.NoPath / CareerPathHomeUiState.NoPath. A role with
 * zero required skills defined (rare -- an AI-assisted role whose overview came back empty)
 * is deliberately NOT a separate state here: it's still a real Success, just with an empty
 * skills list and a reasons[] message explaining why -- the screen renders that directly
 * rather than needing a third sealed variant for it. */
sealed interface CareerTwinUiState {
    data object Loading : CareerTwinUiState
    data class NoRole(val reason: String) : CareerTwinUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : CareerTwinUiState
    data class Success(val twin: CareerTwinDto) : CareerTwinUiState
}

class CareerTwinViewModel(private val careerTwinRepository: CareerTwinRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<CareerTwinUiState>(CareerTwinUiState.Loading)
    val uiState: StateFlow<CareerTwinUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = CareerTwinUiState.Loading
            try {
                val twin = careerTwinRepository.getCareerTwin()
                _uiState.value = if (twin.hasRole) {
                    CareerTwinUiState.Success(twin)
                } else {
                    CareerTwinUiState.NoRole(twin.reasons.firstOrNull() ?: "Select a target role to build your Career Twin.")
                }
            } catch (e: Exception) {
                _uiState.value = CareerTwinUiState.Error(
                    e.toUserMessage("load your Career Twin"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }
}
