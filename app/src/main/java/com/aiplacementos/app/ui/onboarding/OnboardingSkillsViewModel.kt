package com.aiplacementos.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.SkillDto
import com.aiplacementos.app.domain.repository.ProfileRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingSkillsViewModel(private val profileRepository: ProfileRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<SkillDto>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<SkillDto>>> = _uiState.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                // Always Success, even with zero skills -- an empty skill list is this
                // step's normal starting point, not an error/empty condition to special-case.
                _uiState.value = UiState.Success(profileRepository.listSkills())
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't load your skills. Check your connection.")
            }
        }
    }

    /** Toggling a suggested or custom skill by name -- adds it if not already present,
     * removes it (by id) if it is. */
    fun toggleSkill(name: String) {
        val current = (_uiState.value as? UiState.Success<List<SkillDto>>)?.data ?: return
        val existing = current.find { it.name.equals(name, ignoreCase = true) }
        viewModelScope.launch {
            _busy.value = true
            try {
                if (existing != null) {
                    profileRepository.deleteSkill(existing.id)
                } else {
                    profileRepository.addSkill(name, proficiency = null)
                }
                _uiState.value = UiState.Success(profileRepository.listSkills())
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't update your skills. Check your connection.")
            } finally {
                _busy.value = false
            }
        }
    }
}
