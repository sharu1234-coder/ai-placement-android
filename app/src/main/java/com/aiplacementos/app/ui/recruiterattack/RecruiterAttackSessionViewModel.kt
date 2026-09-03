package com.aiplacementos.app.ui.recruiterattack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.RecruiterAttackTurnDto
import com.aiplacementos.app.domain.repository.RecruiterAttackRepository
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Turn-by-turn Q&A flow, same shape as TextInterviewViewModel -- the session itself already
 * exists by the time this loads (created in RecruiterAttackCreateScreen), so unlike the
 * interview flow there is no separate "starting" step. */
sealed interface RecruiterAttackSessionUiState {
    data object Loading : RecruiterAttackSessionUiState
    data class Turn(val turn: RecruiterAttackTurnDto, val submitting: Boolean = false) : RecruiterAttackSessionUiState
    data class Error(val message: String) : RecruiterAttackSessionUiState
    data object Completed : RecruiterAttackSessionUiState
}

class RecruiterAttackSessionViewModel(
    private val sessionId: Int,
    private val recruiterAttackRepository: RecruiterAttackRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RecruiterAttackSessionUiState>(RecruiterAttackSessionUiState.Loading)
    val uiState: StateFlow<RecruiterAttackSessionUiState> = _uiState.asStateFlow()

    init {
        loadNext()
    }

    fun loadNext() {
        viewModelScope.launch {
            _uiState.value = RecruiterAttackSessionUiState.Loading
            try {
                val response = recruiterAttackRepository.nextTurn(sessionId)
                val turn = response.turn
                _uiState.value = if (response.completed || turn == null) {
                    RecruiterAttackSessionUiState.Completed
                } else {
                    RecruiterAttackSessionUiState.Turn(turn)
                }
            } catch (e: Exception) {
                _uiState.value = RecruiterAttackSessionUiState.Error(
                    e.toUserMessage("load the next question")
                )
            }
        }
    }

    fun submitAnswer(answerText: String) {
        val current = _uiState.value
        if (current !is RecruiterAttackSessionUiState.Turn) return

        viewModelScope.launch {
            _uiState.value = current.copy(submitting = true)
            try {
                recruiterAttackRepository.answer(sessionId, current.turn.id, answerText)
                loadNext()
            } catch (e: Exception) {
                _uiState.value = RecruiterAttackSessionUiState.Error(e.toUserMessage("submit your answer"))
            }
        }
    }
}
