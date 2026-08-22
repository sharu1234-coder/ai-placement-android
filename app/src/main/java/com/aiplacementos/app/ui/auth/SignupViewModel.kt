package com.aiplacementos.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.domain.repository.AuthRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class SignupViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Empty)
    val uiState: StateFlow<UiState<Unit>> = _uiState.asStateFlow()

    fun signup(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                authRepository.signup(email, password)
                authRepository.login(email, password)
                _uiState.value = UiState.Success(Unit)
            } catch (e: HttpException) {
                _uiState.value = UiState.Error(
                    when (e.code()) {
                        409 -> "That email is already registered"
                        422 -> "Password must be at least 8 characters"
                        else -> "Sign up failed (${e.code()})"
                    }
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't reach the server. Check your connection.")
            }
        }
    }
}
