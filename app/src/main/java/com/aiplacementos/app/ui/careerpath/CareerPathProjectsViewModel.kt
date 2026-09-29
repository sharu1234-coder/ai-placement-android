package com.aiplacementos.app.ui.careerpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CareerPathProjectDto
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CareerPathProjectsViewModel(
    private val pathId: Int,
    private val careerPathRepository: CareerPathRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<CareerPathProjectDto>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<CareerPathProjectDto>>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val projects = careerPathRepository.getProjects(pathId)
                _uiState.value = if (projects.isEmpty()) UiState.Empty else UiState.Success(projects)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("load projects"))
            }
        }
    }
}
