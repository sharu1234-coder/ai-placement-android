package com.aiplacementos.app.ui.arena

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ArenaAttemptResultDto
import com.aiplacementos.app.domain.repository.ArenaRepository
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ArenaSessionUiState {
    data object InProgress : ArenaSessionUiState
    data class Completing(val error: String? = null) : ArenaSessionUiState
    data class Finished(val result: ArenaAttemptResultDto) : ArenaSessionUiState
}

/** Drives the countdown and the completion call; the actual question/answer flow is the
 * existing TestTakingScreen, reused unchanged and driven directly against [assessmentId] --
 * Arena adds nothing to that flow, only a visible timer above it and a completion call once
 * every question is answered or the timer runs out (see arena_service.complete_attempt: the
 * server independently re-derives whether time actually ran out, this client-side timer is
 * only what drives the UI and the displayed "time taken", never a claim the server trusts). */
class ArenaSessionViewModel(
    private val arenaAttemptId: Int,
    private val timeLimitSeconds: Int,
    private val arenaRepository: ArenaRepository,
) : ViewModel() {
    private val _remainingSeconds = MutableStateFlow(timeLimitSeconds)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _uiState = MutableStateFlow<ArenaSessionUiState>(ArenaSessionUiState.InProgress)
    val uiState: StateFlow<ArenaSessionUiState> = _uiState.asStateFlow()

    private var completing = false

    init {
        viewModelScope.launch {
            while (_remainingSeconds.value > 0 && _uiState.value is ArenaSessionUiState.InProgress) {
                delay(1_000)
                _remainingSeconds.value = (_remainingSeconds.value - 1).coerceAtLeast(0)
            }
            if (_uiState.value is ArenaSessionUiState.InProgress) onQuestionsFinished()
        }
    }

    fun onQuestionsFinished() {
        if (completing) return
        completing = true
        val elapsed = timeLimitSeconds - _remainingSeconds.value
        viewModelScope.launch {
            _uiState.value = ArenaSessionUiState.Completing()
            try {
                val result = arenaRepository.completeAttempt(arenaAttemptId, elapsed)
                _uiState.value = ArenaSessionUiState.Finished(result)
            } catch (e: Exception) {
                completing = false
                _uiState.value = ArenaSessionUiState.Completing(e.toUserMessage("finish this challenge"))
            }
        }
    }

    fun retryCompletion() {
        completing = false
        onQuestionsFinished()
    }
}
