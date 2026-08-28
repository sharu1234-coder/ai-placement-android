package com.aiplacementos.app.ui.common

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Error(val message: String, val isNetworkError: Boolean = false) : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
}
