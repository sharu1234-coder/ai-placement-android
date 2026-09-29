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

enum class ForgotPasswordStep { REQUEST_CODE, RESET_PASSWORD }

class ForgotPasswordViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _step = MutableStateFlow(ForgotPasswordStep.REQUEST_CODE)
    val step: StateFlow<ForgotPasswordStep> = _step.asStateFlow()

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Empty)
    val uiState: StateFlow<UiState<Unit>> = _uiState.asStateFlow()

    var email: String = ""
        private set

    fun requestCode(email: String) {
        this.email = email
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                // The backend always returns the same message whether or not this email is
                // registered (prevents email enumeration) -- so success here just means the
                // request reached the server, not that an account definitely exists.
                authRepository.forgotPassword(email)
                _uiState.value = UiState.Empty
                _step.value = ForgotPasswordStep.RESET_PASSWORD
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("send your reset code"))
            }
        }
    }

    fun resetPassword(code: String, newPassword: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                authRepository.resetPassword(email, code, newPassword)
                _uiState.value = UiState.Success(Unit)
                onDone()
            } catch (e: HttpException) {
                _uiState.value = UiState.Error(
                    if (e.code() == 400) "Invalid or expired code" else "Couldn't reset password (${e.code()})"
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("reset your password"))
            }
        }
    }
}
