package com.aiplacementos.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeData(val greeting: String = "Good morning")

/**
 * Establishes the ViewModel -> UiState -> Compose pattern that every feature screen follows
 * from Stage 4 onward. Loads simulated data since no repository/backend integration exists yet.
 */
class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val uiState: StateFlow<UiState<HomeData>> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            delay(400)
            _uiState.value = UiState.Success(HomeData())
        }
    }
}
