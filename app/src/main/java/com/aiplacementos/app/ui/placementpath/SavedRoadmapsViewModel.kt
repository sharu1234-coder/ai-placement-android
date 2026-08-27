package com.aiplacementos.app.ui.placementpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.PlacementPathDto
import com.aiplacementos.app.domain.repository.PlacementPathRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SavedRoadmapsViewModel(private val repository: PlacementPathRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<PlacementPathDto>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<PlacementPathDto>>> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val history = repository.getHistory()
                _uiState.value = if (history.isEmpty()) UiState.Empty else UiState.Success(history)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't load your saved roadmaps. Check your connection.")
            }
        }
    }
}
