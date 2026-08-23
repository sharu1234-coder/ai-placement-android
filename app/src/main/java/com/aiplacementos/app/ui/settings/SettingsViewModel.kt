package com.aiplacementos.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.domain.repository.AuthRepository
import com.aiplacementos.app.domain.repository.ProfileRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _deleteAccountState = MutableStateFlow<UiState<Unit>>(UiState.Empty)
    val deleteAccountState: StateFlow<UiState<Unit>> = _deleteAccountState.asStateFlow()

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }

    fun deleteAccount(password: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _deleteAccountState.value = UiState.Loading
            try {
                profileRepository.deleteMyAccount(password)
                // The server already made the access token unusable by deleting the user
                // row it was issued for; clearing local tokens too keeps the client in sync.
                authRepository.logout()
                _deleteAccountState.value = UiState.Success(Unit)
                onDeleted()
            } catch (e: HttpException) {
                _deleteAccountState.value = UiState.Error(
                    if (e.code() == 403) "Incorrect password" else "Couldn't delete account (${e.code()})"
                )
            } catch (e: Exception) {
                _deleteAccountState.value = UiState.Error("Couldn't reach the server. Check your connection.")
            }
        }
    }

    fun resetDeleteAccountState() {
        _deleteAccountState.value = UiState.Empty
    }
}
