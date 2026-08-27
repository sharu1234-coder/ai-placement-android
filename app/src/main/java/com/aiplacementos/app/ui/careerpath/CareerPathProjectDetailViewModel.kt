package com.aiplacementos.app.ui.careerpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CareerPathProjectDto
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CareerPathProjectDetailViewModel(
    private val pathId: Int,
    private val projectId: Int,
    private val careerPathRepository: CareerPathRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<CareerPathProjectDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<CareerPathProjectDto>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val project = careerPathRepository.getProjects(pathId).firstOrNull { it.id == projectId }
                _uiState.value = if (project != null) UiState.Success(project) else UiState.Error("This project couldn't be found.")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't load this project. Check your connection and try again.")
            }
        }
    }

    fun setStatus(status: String) {
        viewModelScope.launch {
            try {
                careerPathRepository.markProjectStatus(projectId, status)
                load()
            } catch (e: Exception) {
                // Stays on the previous status; the user can retry the tap.
            }
        }
    }
}
