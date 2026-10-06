package com.aiplacementos.app.ui.arena

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ArenaAttemptStartDto
import com.aiplacementos.app.data.remote.ArenaChallengeDto
import com.aiplacementos.app.data.remote.OperationKeyHolder
import com.aiplacementos.app.domain.repository.ArenaRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ArenaListUiState {
    data object Loading : ArenaListUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : ArenaListUiState
    data class Success(
        val challenges: List<ArenaChallengeDto>,
        val startingKey: String? = null,
        val startError: String? = null,
    ) : ArenaListUiState
}

class ArenaListViewModel(private val arenaRepository: ArenaRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<ArenaListUiState>(ArenaListUiState.Loading)
    val uiState: StateFlow<ArenaListUiState> = _uiState.asStateFlow()

    /**
     * One key per challenge being started.
     *
     * Keyed by challenge rather than held in a single slot: starting DSA and then starting SQL
     * are two intents, while two taps on DSA are one. There is deliberately no in-flight guard
     * here -- startChallenge's only check is that the list loaded -- so the stable key is what
     * holds a double tap to a single attempt.
     */
    private val startKeys = OperationKeyHolder()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ArenaListUiState.Loading
            try {
                _uiState.value = ArenaListUiState.Success(arenaRepository.listChallenges())
            } catch (e: Exception) {
                _uiState.value = ArenaListUiState.Error(
                    e.toUserMessage("load the Placement Arena"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }

    fun startChallenge(challengeKey: String, onStarted: (ArenaAttemptStartDto) -> Unit) {
        val current = _uiState.value
        if (current !is ArenaListUiState.Success) return
        viewModelScope.launch {
            _uiState.value = current.copy(startingKey = challengeKey, startError = null)
            try {
                val start = arenaRepository.startAttempt(
                    challengeKey,
                    startKeys.keyFor(challengeKey),
                )
                startKeys.consume(challengeKey)
                onStarted(start)
            } catch (e: Exception) {
                val latest = _uiState.value
                if (latest is ArenaListUiState.Success && latest.startingKey == challengeKey) {
                    _uiState.value = latest.copy(startingKey = null, startError = e.toUserMessage("start this challenge"))
                }
            }
        }
    }
}
