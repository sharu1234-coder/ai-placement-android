package com.aiplacementos.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.domain.repository.ProfileRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingNameViewModel(private val profileRepository: ProfileRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<ProfileDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<ProfileDto>> = _uiState.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                _uiState.value = UiState.Success(profileRepository.getMyProfile())
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("load your profile"))
            }
        }
    }

    fun save(fullName: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            _saving.value = true
            try {
                profileRepository.updateMyProfile(fullName = fullName, college = null, graduationYear = null)
                onSaved()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("save your profile"))
            } finally {
                _saving.value = false
            }
        }
    }
}
