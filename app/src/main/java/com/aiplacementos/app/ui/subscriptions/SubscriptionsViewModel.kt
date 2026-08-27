package com.aiplacementos.app.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.SubscriptionStatusDto
import com.aiplacementos.app.domain.repository.SubscriptionRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SubscriptionsViewModel(private val repository: SubscriptionRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<SubscriptionStatusDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<SubscriptionStatusDto>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                _uiState.value = UiState.Success(repository.getStatus())
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't load subscription status. Check your connection.")
            }
        }
    }
}
