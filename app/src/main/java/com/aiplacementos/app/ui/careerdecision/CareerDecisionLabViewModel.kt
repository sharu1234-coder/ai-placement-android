package com.aiplacementos.app.ui.careerdecision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CareerDecisionSimulationDto
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.domain.repository.CareerDecisionRepository
import com.aiplacementos.app.domain.repository.ProfileRepository
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val DEFAULT_WEEKLY_HOURS = 10
private const val MAX_SELECTED_ROLES = 3
private const val MIN_SELECTED_ROLES = 2

sealed interface CareerDecisionUiState {
    data object LoadingRoles : CareerDecisionUiState
    data class RolesError(val message: String) : CareerDecisionUiState
    data class Ready(
        val availableRoles: List<RoleDto>,
        val selectedRoleIds: List<Int> = emptyList(),
        val weeklyHours: Int = DEFAULT_WEEKLY_HOURS,
        val simulating: Boolean = false,
        val simulateError: String? = null,
        val result: CareerDecisionSimulationDto? = null,
    ) : CareerDecisionUiState {
        val canCompare: Boolean get() = selectedRoleIds.size in MIN_SELECTED_ROLES..MAX_SELECTED_ROLES
    }
}

class CareerDecisionLabViewModel(
    private val careerDecisionRepository: CareerDecisionRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CareerDecisionUiState>(CareerDecisionUiState.LoadingRoles)
    val uiState: StateFlow<CareerDecisionUiState> = _uiState.asStateFlow()

    init {
        loadRoles()
    }

    fun loadRoles() {
        viewModelScope.launch {
            _uiState.value = CareerDecisionUiState.LoadingRoles
            try {
                val roles = profileRepository.getRoles()
                _uiState.value = CareerDecisionUiState.Ready(availableRoles = roles)
            } catch (e: Exception) {
                _uiState.value = CareerDecisionUiState.RolesError(e.toUserMessage("load roles to compare"))
            }
        }
    }

    fun toggleRole(roleId: Int) {
        val current = _uiState.value
        if (current !is CareerDecisionUiState.Ready) return
        val selected = when {
            roleId in current.selectedRoleIds -> current.selectedRoleIds - roleId
            current.selectedRoleIds.size < MAX_SELECTED_ROLES -> current.selectedRoleIds + roleId
            else -> current.selectedRoleIds
        }
        _uiState.value = current.copy(selectedRoleIds = selected)
    }

    fun setWeeklyHours(hours: Int) {
        val current = _uiState.value
        if (current is CareerDecisionUiState.Ready) {
            _uiState.value = current.copy(weeklyHours = hours)
        }
    }

    /** Called on slider release. Before a first Compare, this only stores the value locally
     * (via setWeeklyHours) -- once a result exists, sliding again re-runs the same simulate
     * call so the "what if I study N hours/week?" comparison stays live. */
    fun onWeeklyHoursSettled() {
        val current = _uiState.value
        if (current is CareerDecisionUiState.Ready && current.result != null) compare()
    }

    fun compare() {
        val current = _uiState.value
        if (current !is CareerDecisionUiState.Ready || !current.canCompare) return
        viewModelScope.launch {
            _uiState.value = current.copy(simulating = true, simulateError = null)
            try {
                val result = careerDecisionRepository.simulate(current.selectedRoleIds, current.weeklyHours)
                val latest = _uiState.value
                if (latest is CareerDecisionUiState.Ready) {
                    _uiState.value = latest.copy(
                        simulating = false, result = result, weeklyHours = result.weeklyHoursUsed ?: latest.weeklyHours,
                    )
                }
            } catch (e: Exception) {
                val latest = _uiState.value
                if (latest is CareerDecisionUiState.Ready) {
                    _uiState.value = latest.copy(simulating = false, simulateError = e.toUserMessage("run this comparison"))
                }
            }
        }
    }
}
