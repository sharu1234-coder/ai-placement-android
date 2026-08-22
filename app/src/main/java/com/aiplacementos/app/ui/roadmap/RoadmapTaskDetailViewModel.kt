package com.aiplacementos.app.ui.roadmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.RoadmapTaskDto
import com.aiplacementos.app.domain.repository.RoadmapRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoadmapTaskDetailViewModel(
    private val taskId: Int,
    jdId: Int,
    private val roadmapRepository: RoadmapRepository,
) : ViewModel() {
    private val scopedJdId = jdId.takeIf { it != 0 }

    private val _uiState = MutableStateFlow<UiState<RoadmapTaskDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<RoadmapTaskDto>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val task = roadmapRepository.getCurrentRoadmap(scopedJdId)?.tasks?.find { it.id == taskId }
                _uiState.value = if (task != null) UiState.Success(task) else UiState.Empty
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't load this task. Check your connection and try again.")
            }
        }
    }

    fun updateStatus(status: String) {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Success(roadmapRepository.updateTaskStatus(taskId, status))
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't update this task. Check your connection and try again.")
            }
        }
    }
}
