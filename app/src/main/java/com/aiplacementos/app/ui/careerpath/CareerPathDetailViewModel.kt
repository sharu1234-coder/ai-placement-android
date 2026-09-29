package com.aiplacementos.app.ui.careerpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CareerPathDto
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
                _uiState.value = UiState.Success(careerPathRepository.refresh(pathId))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("refresh this Career Path"))
            }
        }
    }
}
