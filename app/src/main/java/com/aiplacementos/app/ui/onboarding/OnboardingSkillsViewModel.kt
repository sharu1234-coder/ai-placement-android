package com.aiplacementos.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.OperationKeyHolder
import com.aiplacementos.app.data.remote.SkillDto
import com.aiplacementos.app.domain.repository.ProfileRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingSkillsViewModel(private val profileRepository: ProfileRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<SkillDto>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<SkillDto>>> = _uiState.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /**
     * One key per skill being added.
     *
     * Keyed by the name exactly as it is sent -- not lowercased, because the server fingerprints
     * the name it receives, and a client identity that is *less* discriminating than that
     * fingerprint would hand "react" the key already used for "React" and get a conflict back.
     *
     * Adding is the only direction that needs this. Deleting by id is already idempotent: the
     * row is either there to remove or it is not.
     */
    private val addSkillKeys = OperationKeyHolder()

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
                _uiState.value = UiState.Error(e.toUserMessage("load your skills"))
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
                    profileRepository.addSkill(
                        name,
                        proficiency = null,
                        idempotencyKey = addSkillKeys.keyFor(name),
                    )
                    addSkillKeys.consume(name)
                }
                _uiState.value = UiState.Success(profileRepository.listSkills())
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("update your skills"))
            } finally {
                _busy.value = false
            }
        }
    }
}
