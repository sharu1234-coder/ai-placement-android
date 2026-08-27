package com.aiplacementos.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.domain.repository.ProfileRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** [isSecondary] routes selection to the secondary-role endpoint instead of primary, and
 * (per the backend's design -- secondary is catalog-only) disables custom-role entry. */
class RoleSelectionViewModel(
    private val profileRepository: ProfileRepository,
    private val isSecondary: Boolean = false,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<RoleDto>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<RoleDto>>> = _uiState.asStateFlow()

    private val _selecting = MutableStateFlow(false)
    val selecting: StateFlow<Boolean> = _selecting.asStateFlow()

    val allowCustomRole: Boolean = !isSecondary

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val roles = profileRepository.getRoles()
                _uiState.value = if (roles.isEmpty()) UiState.Empty else UiState.Success(roles)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't load roles. Check your connection.")
            }
        }
    }

    fun selectRole(roleId: Int, onSelected: () -> Unit) {
        viewModelScope.launch {
            _selecting.value = true
            try {
                if (isSecondary) profileRepository.selectSecondaryRole(roleId) else profileRepository.selectRole(roleId)
                onSelected()
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't select that role. Check your connection.")
            } finally {
                _selecting.value = false
            }
        }
    }

    fun selectCustomRole(customRole: String, onSelected: () -> Unit) {
        if (isSecondary || customRole.isBlank()) return
        viewModelScope.launch {
            _selecting.value = true
            try {
                profileRepository.selectRole(roleId = null, customRole = customRole)
                onSelected()
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't select that role. Check your connection.")
            } finally {
                _selecting.value = false
            }
        }
    }
}
