package com.aiplacementos.app.ui.placementpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.PlacementPathDto
import com.aiplacementos.app.data.remote.PlacementPathTopicDto
import com.aiplacementos.app.domain.repository.PlacementPathRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * A placement-path screen has more sub-phases than the generic UiState<T> can model -- "not
 * generated yet" is a real, user-facing state distinct from a network error, and a running
 * generate action should keep showing the current path rather than flashing a blank loading
 * screen. Deliberate exception to UiState<T>, same as RoadmapUiState/TestTakingUiState.
 */
sealed interface PlacementPathUiState {
    data object Loading : PlacementPathUiState
    data object NoPath : PlacementPathUiState
    data class Error(val message: String) : PlacementPathUiState
    data class Success(val path: PlacementPathDto, val isBusy: Boolean = false) : PlacementPathUiState
}

class PlacementPathViewModel(private val placementPathRepository: PlacementPathRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<PlacementPathUiState>(PlacementPathUiState.Loading)
    val uiState: StateFlow<PlacementPathUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = PlacementPathUiState.Loading
            try {
                val current = placementPathRepository.getCurrent()
                _uiState.value = if (current != null) {
                    PlacementPathUiState.Success(current)
                } else {
                    PlacementPathUiState.NoPath
                }
            } catch (e: Exception) {
                _uiState.value = PlacementPathUiState.Error(
                    "Couldn't load your roadmap. Check your connection and try again."
                )
            }
        }
    }

    /** Used for both first-time generation and explicit regeneration -- the backend treats
     * them as the same operation (see placement_path_service.generate_placement_path). */
    fun generate() {
        viewModelScope.launch {
            _uiState.value = PlacementPathUiState.Loading
            try {
                _uiState.value = PlacementPathUiState.Success(placementPathRepository.generate())
            } catch (e: HttpException) {
                _uiState.value = PlacementPathUiState.Error(
                    if (e.code() == 422) {
                        "Upload a resume with placement-relevant skills to generate your roadmap."
                    } else {
                        "Couldn't generate your roadmap (${e.code()})."
                    }
                )
            } catch (e: Exception) {
                _uiState.value = PlacementPathUiState.Error("Couldn't reach the server. Check your connection.")
            }
        }
    }

    fun startTopic(topicId: Int) {
        val successState = _uiState.value as? PlacementPathUiState.Success ?: return
        viewModelScope.launch {
            try {
                val updatedTopic = placementPathRepository.startTopic(topicId)
                _uiState.value = successState.copy(path = successState.path.replacing(updatedTopic))
            } catch (e: Exception) {
                // A single topic-start failing isn't worth a full error screen -- the topic
                // simply keeps showing its previous status.
            }
        }
    }

    /** Unlike startTopic, completing a topic can unlock its successor in the same track --
     * a local single-field patch would leave that successor showing a stale "locked" badge
     * until the next reload, so this reloads the whole path from the server instead of
     * patching in place. */
    fun markComplete(topicId: Int) {
        if (_uiState.value !is PlacementPathUiState.Success) return
        viewModelScope.launch {
            try {
                placementPathRepository.markComplete(topicId)
                load()
            } catch (e: Exception) {
                // A single completion failing isn't worth a full error screen -- the topic
                // simply keeps showing its previous status, and the user can retry.
            }
        }
    }
}

/** Patches a single topic in place across every stage -- used for optimistic partial updates
 * that don't affect any other topic's lock state (see startTopic). */
private fun PlacementPathDto.replacing(updatedTopic: PlacementPathTopicDto): PlacementPathDto = copy(
    stages = stages.map { stage ->
        stage.copy(topics = stage.topics.map { if (it.id == updatedTopic.id) updatedTopic else it })
    },
)
