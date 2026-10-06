package com.aiplacementos.app.ui.careerpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.data.remote.OperationKeyHolder
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CareerPathDetailViewModel(
    private val pathId: Int,
    private val careerPathRepository: CareerPathRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<CareerPathDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<CareerPathDto>> = _uiState.asStateFlow()

    /**
     * The refresh key. The path id is fixed for this screen and is all the server fingerprints,
     * so one identity is enough.
     *
     * Refresh only. Career Path *generation* keeps its Phase 14 durable pending record and its
     * Phase 16 transport exemption and is not touched from here: generation runs for minutes and
     * recovers by reconciling against server state, while refresh re-plans a path that already
     * exists and recovers by being asked again.
     */
    private val refreshKeys = OperationKeyHolder()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                _uiState.value = UiState.Success(careerPathRepository.getPath(pathId))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("load this Career Path"))
            }
        }
    }

    /** A single skill/project completing can change its phase's overall status (and the
     * readiness score), so this reloads the whole path rather than patching one field --
     * simplest-correct, same reasoning as PlacementPathViewModel.markComplete(). */
    fun markSkillItemComplete(itemId: Int) {
        viewModelScope.launch {
            try {
                careerPathRepository.markSkillItemStatus(itemId, "completed")
                load()
            } catch (e: Exception) {
                // A single item update failing isn't worth a full error screen.
            }
        }
    }

    fun markProjectComplete(projectId: Int) {
        viewModelScope.launch {
            try {
                careerPathRepository.markProjectStatus(projectId, "completed")
                load()
            } catch (e: Exception) {
                // A single project update failing isn't worth a full error screen.
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val refreshed = careerPathRepository.refresh(pathId, refreshKeys.keyFor())
                refreshKeys.consume()
                _uiState.value = UiState.Success(refreshed)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("refresh this Career Path"))
            }
        }
    }
}
