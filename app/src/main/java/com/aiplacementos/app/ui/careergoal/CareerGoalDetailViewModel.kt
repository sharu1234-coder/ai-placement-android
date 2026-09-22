package com.aiplacementos.app.ui.careergoal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CareerGoalDetailDto
import com.aiplacementos.app.domain.repository.CareerGoalRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CareerGoalDetailUiState {
    data object Loading : CareerGoalDetailUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : CareerGoalDetailUiState
    data class Success(val goal: CareerGoalDetailDto) : CareerGoalDetailUiState
}

/**
 * The detail screen's state.
 *
 * Analysis is tracked separately from the page load on purpose. Running it must not blank the
 * screen the student is reading, and a failed analysis must not turn a perfectly good goal into
 * an error page -- the goal is still there, still complete, and still has its job description.
 */
class CareerGoalDetailViewModel(
    private val careerGoalId: Int,
    private val repository: CareerGoalRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CareerGoalDetailUiState>(CareerGoalDetailUiState.Loading)
    val uiState: StateFlow<CareerGoalDetailUiState> = _uiState.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    /** Why the last analysis failed, for an inline notice beside the Analyze button. Never
     * replaces the page, and never fabricates an analysis in its place. */
    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    private val _isDeleted = MutableStateFlow(false)
    val isDeleted: StateFlow<Boolean> = _isDeleted.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = CareerGoalDetailUiState.Loading
            try {
                _uiState.value = CareerGoalDetailUiState.Success(repository.getGoal(careerGoalId))
            } catch (e: Exception) {
                _uiState.value = CareerGoalDetailUiState.Error(
                    e.toUserMessage("load this career goal"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }

    /** The one call in this feature that costs anything, so it is guarded against a double tap
     * rather than relying on the server's rate limit to absorb one. */
    fun analyze() {
        if (_isAnalyzing.value) return
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisError.value = null
            try {
                repository.analyzeGoal(careerGoalId)
                _uiState.value = CareerGoalDetailUiState.Success(repository.getGoal(careerGoalId))
            } catch (e: Exception) {
                _analysisError.value = e.toUserMessage("analyze this job description")
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun setStatus(status: String) {
        viewModelScope.launch {
            try {
                repository.setStatus(careerGoalId, status)
                _uiState.value = CareerGoalDetailUiState.Success(repository.getGoal(careerGoalId))
            } catch (e: Exception) {
                _analysisError.value = e.toUserMessage("update this career goal")
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                repository.deleteGoal(careerGoalId)
                _isDeleted.value = true
            } catch (e: Exception) {
                _analysisError.value = e.toUserMessage("delete this career goal")
            }
        }
    }

    fun dismissAnalysisError() {
        _analysisError.value = null
    }
}
