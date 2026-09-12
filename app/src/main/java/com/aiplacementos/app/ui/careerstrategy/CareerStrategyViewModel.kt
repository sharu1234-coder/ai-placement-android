package com.aiplacementos.app.ui.careerstrategy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CareerStrategyDto
import com.aiplacementos.app.data.remote.JobStrategyDto
import com.aiplacementos.app.data.remote.StrategyHistoryDto
import com.aiplacementos.app.data.remote.TargetComparisonDto
import com.aiplacementos.app.domain.repository.CareerStrategyRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Career Strategy screen state.
 *
 * `NoRole` and `InsufficientData` are real, user-facing answers rather than errors -- the same
 * rule the backend follows. A student with nothing recorded has not failed at anything; they
 * have simply not done the thing yet, and telling them so with a way forward is the honest
 * screen. Turning either into an error state would be the app blaming the student for its own
 * lack of data.
 *
 * There is deliberately no `PremiumLocked` screen state. The strategy screen is free -- what is
 * Premium are sections *within* it, which the server omits from the payload and names in
 * `lockedSections`. A whole-screen paywall would leave a free student worse informed than
 * before the feature existed.
 */
sealed interface CareerStrategyUiState {
    data object Loading : CareerStrategyUiState
    data class NoRole(val reason: String) : CareerStrategyUiState
    data class InsufficientData(val reason: String, val signalCount: Int) : CareerStrategyUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : CareerStrategyUiState
    data class Success(val strategy: CareerStrategyDto) : CareerStrategyUiState
}

/**
 * History, targets and job analysis each load on demand rather than with the main screen.
 *
 * `Locked` is a first-class state, not an error. A 403 from a Premium endpoint is the server
 * working correctly, and rendering "something went wrong" for it would be both wrong and
 * alarming. Every one of these sections distinguishes "you cannot see this" from "we could not
 * load this", because they call for completely different screens.
 */
sealed interface StrategySectionState<out T> {
    data object Idle : StrategySectionState<Nothing>
    data object Loading : StrategySectionState<Nothing>
    data object Locked : StrategySectionState<Nothing>
    data class Error(val message: String, val isNetworkError: Boolean = false) : StrategySectionState<Nothing>
    data class Loaded<T>(val data: T) : StrategySectionState<T>
}

private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404

class CareerStrategyViewModel(
    private val repository: CareerStrategyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CareerStrategyUiState>(CareerStrategyUiState.Loading)
    val uiState: StateFlow<CareerStrategyUiState> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<StrategySectionState<StrategyHistoryDto>>(StrategySectionState.Idle)
    val history: StateFlow<StrategySectionState<StrategyHistoryDto>> = _history.asStateFlow()

    private val _targets = MutableStateFlow<StrategySectionState<TargetComparisonDto>>(StrategySectionState.Idle)
    val targets: StateFlow<StrategySectionState<TargetComparisonDto>> = _targets.asStateFlow()

    private val _jobStrategy = MutableStateFlow<StrategySectionState<JobStrategyDto>>(StrategySectionState.Idle)
    val jobStrategy: StateFlow<StrategySectionState<JobStrategyDto>> = _jobStrategy.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = CareerStrategyUiState.Loading
            try {
                val strategy = repository.getStrategy()
                _uiState.value = when (strategy.state) {
                    // Both come straight from the backend's own wording. The screen never
                    // invents a reason -- if the server can explain why it cannot help yet,
                    // that explanation is the one the student should read.
                    "FOCUS_ON_TARGET_ROLE" -> CareerStrategyUiState.NoRole(
                        strategy.stateReason.ifBlank {
                            "Choose a target role and the rest of your strategy follows from it."
                        },
                    )
                    "INSUFFICIENT_INFORMATION" -> CareerStrategyUiState.InsufficientData(
                        reason = strategy.stateReason.ifBlank {
                            "There isn't enough recorded yet to give you a reliable strategy."
                        },
                        signalCount = strategy.signalCount,
                    )
                    else -> CareerStrategyUiState.Success(strategy)
                }
            } catch (e: Exception) {
                _uiState.value = CareerStrategyUiState.Error(
                    e.toUserMessage("load your career strategy"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }

    fun loadHistory() {
        if (_history.value is StrategySectionState.Loading) return
        viewModelScope.launch {
            _history.value = StrategySectionState.Loading
            _history.value = runSection("load your strategy history") { repository.getHistory() }
        }
    }

    fun loadTargets() {
        if (_targets.value is StrategySectionState.Loading) return
        viewModelScope.launch {
            _targets.value = StrategySectionState.Loading
            _targets.value = runSection("compare your target roles") { repository.getTargets() }
        }
    }

    fun setTargets(roleIds: List<Int>) {
        viewModelScope.launch {
            _targets.value = StrategySectionState.Loading
            _targets.value = runSection("save your target roles") { repository.setTargets(roleIds) }
        }
    }

    fun analyzeJob(listingId: Int) {
        viewModelScope.launch {
            _jobStrategy.value = StrategySectionState.Loading
            _jobStrategy.value = runSection("analyse this job") { repository.getJobStrategy(listingId) }
        }
    }

    fun clearJobStrategy() {
        _jobStrategy.value = StrategySectionState.Idle
    }

    /**
     * Shared call wrapper, and the only place the paywall/error distinction is made.
     *
     * A 403 means the server refused on entitlement grounds, which is a correct outcome and
     * must render as an upgrade prompt. A 404 on the job endpoint means the listing genuinely
     * is not there -- reported as a real message rather than silently showing an empty card,
     * because "the job is gone" and "the job has no blockers" look identical otherwise.
     */
    private suspend fun <T> runSection(action: String, block: suspend () -> T): StrategySectionState<T> =
        try {
            StrategySectionState.Loaded(block())
        } catch (e: HttpException) {
            if (e.code() == HTTP_FORBIDDEN) {
                StrategySectionState.Locked
            } else if (e.code() == HTTP_NOT_FOUND) {
                StrategySectionState.Error("We couldn't find that any more. It may have been removed.")
            } else {
                StrategySectionState.Error(e.toUserMessage(action))
            }
        } catch (e: Exception) {
            StrategySectionState.Error(
                e.toUserMessage(action),
                isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
            )
        }
}
