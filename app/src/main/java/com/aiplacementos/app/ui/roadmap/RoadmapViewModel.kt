package com.aiplacementos.app.ui.roadmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.OperationKeyHolder
import com.aiplacementos.app.data.remote.RoadmapDto
import com.aiplacementos.app.domain.repository.RoadmapRepository
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * A roadmap screen has more sub-phases than the generic UiState<T> can model -- "generated
 * yet at all" is a real, user-facing state distinct from a network error, and a running
 * reassess/generate action should keep showing the current roadmap rather than flashing a
 * blank loading screen. A deliberate exception to UiState<T>, same as
 * TestTakingUiState/TextInterviewUiState/VoiceInterviewUiState.
 */
sealed interface RoadmapUiState {
    data object Loading : RoadmapUiState
    data object NoRoadmap : RoadmapUiState
    data class Error(val message: String) : RoadmapUiState
    data class Success(val roadmap: RoadmapDto, val isBusy: Boolean = false) : RoadmapUiState
}

class RoadmapViewModel(
    /** 0 means role-based scope (no specific JD) -- same sentinel convention used for
     * jdId elsewhere in the nav graph (see Screen.kt). */
    private val jdId: Int,
    private val autoGenerateIfMissing: Boolean,
    private val roadmapRepository: RoadmapRepository,
) : ViewModel() {
    private val scopedJdId = jdId.takeIf { it != 0 }

    private val _uiState = MutableStateFlow<RoadmapUiState>(RoadmapUiState.Loading)
    val uiState: StateFlow<RoadmapUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RoadmapUiState.Loading
            try {
                val current = roadmapRepository.getCurrentRoadmap(scopedJdId)
                _uiState.value = when {
                    current != null -> RoadmapUiState.Success(current)
                    autoGenerateIfMissing -> {
                        generateInternal()
                        return@launch
                    }
                    else -> RoadmapUiState.NoRoadmap
                }
            } catch (e: Exception) {
                _uiState.value = RoadmapUiState.Error(
                    e.toUserMessage("load your roadmap")
                )
            }
        }
    }

    // One key per generation the student asked for, minted on first use and kept until that
    // generation succeeds. In memory rather than SavedStateHandle because the operation cannot
    // outlive this screen: a roadmap generation the student navigated away from is not resumed.
    //
    // Keyed by the JD scope and the duration, because the server hashes both into its request
    // fingerprint. A single slot was wrong: generating for 30 days, failing, choosing 60 and
    // trying again sent the 30-day key with a 60-day body, and the server answered 409. Keyed
    // this way the 60-day attempt is honestly a new operation while a retry of the 30-day one
    // is still the same operation.
    private val generateKeys = OperationKeyHolder()

    // Reassess is fingerprinted on the roadmap id alone, which can change under this screen
    // when a new roadmap is generated -- so it gets its own identity rather than a slot.
    private val reassessKeys = OperationKeyHolder()

    private fun generateIdentity(durationDays: Int) = "${scopedJdId ?: 0}\u0000$durationDays"

    fun generate(durationDays: Int = 30) {
        viewModelScope.launch {
            _uiState.value = RoadmapUiState.Loading
            generateInternal(durationDays)
        }
    }

    private suspend fun generateInternal(durationDays: Int = 30) {
        try {
            val identity = generateIdentity(durationDays)
            val roadmap = roadmapRepository.generateRoadmap(
                scopedJdId, durationDays, generateKeys.keyFor(identity),
            )
            // Cleared only on success, so the *next* deliberate generation is a new operation
            // while every retry of this one -- transport or an explicit tap on the error -- is
            // still the same one. A generation that committed before its reply was lost is then
            // returned rather than run again, which is the whole point of holding the key.
            generateKeys.consume(identity)
            _uiState.value = RoadmapUiState.Success(roadmap)
        } catch (e: HttpException) {
            _uiState.value = RoadmapUiState.Error(
                if (e.code() == 422) {
                    "Complete a test or interview (or paste and analyze a JD) before generating a roadmap."
                } else {
                    "Couldn't generate your roadmap (${e.code()})."
                }
            )
        } catch (e: Exception) {
            _uiState.value = RoadmapUiState.Error(e.toUserMessage("generate your roadmap"))
        }
    }

    fun reassess() {
        val busyState = (_uiState.value as? RoadmapUiState.Success)?.copy(isBusy = true) ?: return
        viewModelScope.launch {
            _uiState.value = busyState
            try {
                val identity = busyState.roadmap.id.toString()
                val roadmap = roadmapRepository.reassessRoadmap(
                    busyState.roadmap.id, reassessKeys.keyFor(identity),
                )
                reassessKeys.consume(identity)
                _uiState.value = RoadmapUiState.Success(roadmap)
            } catch (e: Exception) {
                _uiState.value = RoadmapUiState.Error(e.toUserMessage("reassess your roadmap"))
            }
        }
    }

    fun updateTaskStatus(taskId: Int, status: String) {
        val successState = _uiState.value as? RoadmapUiState.Success ?: return
        viewModelScope.launch {
            try {
                val updatedTask = roadmapRepository.updateTaskStatus(taskId, status)
                val updatedTasks = successState.roadmap.tasks.map { if (it.id == taskId) updatedTask else it }
                _uiState.value = successState.copy(roadmap = successState.roadmap.copy(tasks = updatedTasks))
            } catch (e: Exception) {
                // A single toggle failing isn't worth a full error screen -- the task simply
                // keeps showing its previous status, and the user can retry the tap.
            }
        }
    }
}
