package com.aiplacementos.app.ui.atscheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.OperationKey
import com.aiplacementos.app.domain.repository.AtsCheckRepository
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.Job
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

    /** Terminal. The screen navigates off this state rather than the ViewModel invoking a
     * navigation callback it captured earlier: the captured lambda closes over the
     * NavHostController that existed when start() was called, and rememberNavController() hands
     * out a *new* controller instance after a configuration change, so a callback fired by a
     * coroutine that outlived a rotation would have been navigating a detached controller.
     * Same state-driven pattern TextInterviewViewModel and RecruiterAttackSessionViewModel
     * already use for their own terminal steps. */
    data class Completed(val checkId: Int) : AtsCheckAnalyzingUiState
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

    // The one analysis currently in flight. AtsCheckAnalyzingScreen kicks this off from a
    // LaunchedEffect(Unit), and that effect re-runs on every recomposition of a *new*
    // composition -- which is exactly what a screen rotation produces, while this ViewModel
    // itself survives. Without this guard a rotation mid-analysis fired a second
    // POST /ats-check: a second full (paid, server-side AI) resume analysis, a duplicate
    // report row, and a race between two onComplete callbacks over which checkId the student
    // is navigated to. Double-tapping "Try Again" on the error state did the same thing.
    private var runningJob: Job? = null

    // The analysis this screen is for, named once. runningJob already stops a rotation from
    // starting a second one, and Phase 24 showed what it cannot stop: a process death, or the
    // transport replaying a request whose paid analysis had already committed.
    private val analysisKey: String by lazy { OperationKey.newKey() }

    /** Idempotent while an analysis is in flight -- a repeat call (rotation re-running the
     * screen's LaunchedEffect, or an impatient second tap on "Try Again") joins the existing
     * run instead of starting a competing one. */
    fun start() {
        if (runningJob?.isActive == true) return
        if (_uiState.value is AtsCheckAnalyzingUiState.Completed) return
        runningJob = viewModelScope.launch {
            _uiState.value = AtsCheckAnalyzingUiState.InProgress(0)
            val networkDeferred = async { runCatching { atsCheckRepository.createAtsCheck(resumeId, targetRole, analysisKey) } }

            for (stage in 0 until AtsCheckStageLabels.size - 1) {
                _uiState.value = AtsCheckAnalyzingUiState.InProgress(stage)
                delay(STAGE_DELAY_MS)
                if (networkDeferred.isCompleted) break
            }
            _uiState.value = AtsCheckAnalyzingUiState.InProgress(AtsCheckStageLabels.size - 1)

            networkDeferred.await()
                .onSuccess { report -> _uiState.value = AtsCheckAnalyzingUiState.Completed(report.id) }
                .onFailure { e ->
                    _uiState.value = AtsCheckAnalyzingUiState.Error(e.toUserMessage("analyze your resume"))
                }
        }
    }
}
