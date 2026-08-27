package com.aiplacementos.app.ui.careerpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CareerPathSummaryDto
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CareerPathSavedRolesViewModel(private val careerPathRepository: CareerPathRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<CareerPathSummaryDto>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<CareerPathSummaryDto>>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val all = careerPathRepository.getAll()
                _uiState.value = if (all.isEmpty()) UiState.Empty else UiState.Success(all)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't load your saved roles. Check your connection and try again.")
            }
        }
    }

    fun setCurrent(pathId: Int) {
        viewModelScope.launch {
            try {
                careerPathRepository.setCurrent(pathId)
                load()
            } catch (e: Exception) {
                // The list simply stays as it was; the user can retry the tap.
            }
        }
    }
}
