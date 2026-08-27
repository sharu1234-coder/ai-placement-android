package com.aiplacementos.app.ui.placementpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.PlacementPathTopicDto
import com.aiplacementos.app.domain.repository.PlacementPathRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlacementPathTopicDetailViewModel(
    private val topicId: Int,
    private val placementPathRepository: PlacementPathRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<PlacementPathTopicDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<PlacementPathTopicDto>> = _uiState.asStateFlow()

    private val _prerequisite = MutableStateFlow<PlacementPathTopicDto?>(null)
    val prerequisite: StateFlow<PlacementPathTopicDto?> = _prerequisite.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val allTopics = placementPathRepository.getCurrent()?.stages?.flatMap { it.topics }.orEmpty()
                val topic = allTopics.find { it.id == topicId }
                _uiState.value = if (topic != null) UiState.Success(topic) else UiState.Empty
                _prerequisite.value = allTopics.find { it.id == topic?.prerequisiteTopicId }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't load this topic. Check your connection and try again.")
            }
        }
    }

    /** Starts a practice session for this topic (get-or-creates the scoped blueprint
     * server-side) and invokes [onReady] with its id so the screen can navigate into the
     * existing test flow -- this ViewModel never launches navigation itself. Called directly
     * from this suspend function (a one-shot action) rather than exposed as a StateFlow the
     * screen observes: a StateFlow's last value survives recomposition, so pressing Back into
     * this screen would re-fire the same navigation every time. */
    fun practice(onReady: (blueprintId: Int) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(placementPathRepository.practice(topicId))
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't start practice. Check your connection and try again.")
            }
        }
    }

    fun markComplete() {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Success(placementPathRepository.markComplete(topicId))
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't mark this topic complete. Check your connection.")
            }
        }
    }
}
