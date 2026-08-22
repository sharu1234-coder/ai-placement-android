package com.aiplacementos.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.ui.common.UiState
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeData(val greeting: String)

private fun timeOfDayGreeting(): String = when (LocalTime.now().hour) {
    in 0..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

/**
 * Establishes the ViewModel -> UiState -> Compose pattern that every feature screen follows
 * from Stage 4 onward. The greeting is real (device time), not simulated -- there's no
 * readiness-score dashboard data yet (that's Stage 11's weakness/roadmap engine), so
 * HomeScreen shows an honest "get started" prompt instead of a placeholder number.
 */
class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val uiState: StateFlow<UiState<HomeData>> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Success(HomeData(greeting = timeOfDayGreeting()))
        }
    }
}
