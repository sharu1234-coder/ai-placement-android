package com.aiplacementos.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.domain.repository.AuthRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Empty)
    val uiState: StateFlow<UiState<Unit>> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                authRepository.login(email, password)
                _uiState.value = UiState.Success(Unit)
            } catch (e: HttpException) {
                _uiState.value = UiState.Error(
                    if (e.code() == 401) "Incorrect email or password" else "Login failed (${e.code()})"
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("sign you in"))
            }
        }
    }
}
