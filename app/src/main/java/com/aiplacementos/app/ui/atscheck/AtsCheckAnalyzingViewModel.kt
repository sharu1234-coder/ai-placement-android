package com.aiplacementos.app.ui.atscheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.domain.repository.AtsCheckRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

val AtsCheckStageLabels = listOf(
    "Reading resume file",
    "Extracting text & sections",
    "Checking ATS compatibility",
    "Analyzing content quality",
    "Evaluating skills & experience",
    "Generating recommendations",
    "Calculating ATS score",
)

private const val STAGE_DELAY_MS = 900L

sealed interface AtsCheckAnalyzingUiState {
    data class InProgress(val stageIndex: Int) : AtsCheckAnalyzingUiState
    data class Error(val message: String) : AtsCheckAnalyzingUiState
}

/**
 * There is exactly one real completion signal (the POST response) -- no SSE/polling
 * progress infrastructure exists or is proposed for this. The 7 stages are client-paced,
 * racing the real network call: the last stage is never timer-driven alone, it stays
 * "active" until the real response lands, however long that takes. If the network call
 * finishes (success or failure) before the stage animation does, we stop advancing and
 * resolve immediately instead of forcing a needless wait -- no stage is ever shown "done"
 * before it plausibly is, and a fast or failing response is never artificially delayed.
 */
class AtsCheckAnalyzingViewModel(
    private val resumeId: Int,
    private val targetRole: String?,
    private val atsCheckRepository: AtsCheckRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AtsCheckAnalyzingUiState>(AtsCheckAnalyzingUiState.InProgress(0))
    val uiState: StateFlow<AtsCheckAnalyzingUiState> = _uiState

    fun start(onComplete: (checkId: Int) -> Unit) {
        viewModelScope.launch {
            _uiState.value = AtsCheckAnalyzingUiState.InProgress(0)
            val networkDeferred = async { runCatching { atsCheckRepository.createAtsCheck(resumeId, targetRole) } }

            for (stage in 0 until AtsCheckStageLabels.size - 1) {
                _uiState.value = AtsCheckAnalyzingUiState.InProgress(stage)
                delay(STAGE_DELAY_MS)
                if (networkDeferred.isCompleted) break
            }
            _uiState.value = AtsCheckAnalyzingUiState.InProgress(AtsCheckStageLabels.size - 1)

            networkDeferred.await()
                .onSuccess { report -> onComplete(report.id) }
                .onFailure { e ->
                    _uiState.value = AtsCheckAnalyzingUiState.Error(e.message ?: "Couldn't analyze your resume.")
                }
        }
    }
}
