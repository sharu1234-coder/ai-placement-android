package com.aiplacementos.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.domain.repository.AuthRepository
import kotlinx.coroutines.launch

class SettingsViewModel(private val authRepository: AuthRepository) : ViewModel() {
    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }
}
