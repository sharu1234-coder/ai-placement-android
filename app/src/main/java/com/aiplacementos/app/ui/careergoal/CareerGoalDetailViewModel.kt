package com.aiplacementos.app.ui.careergoal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CareerGoalDetailDto
import com.aiplacementos.app.data.remote.OperationKeyHolder
import com.aiplacementos.app.data.remote.SavedJobDto
import com.aiplacementos.app.domain.repository.CareerGoalRepository
import com.aiplacementos.app.domain.repository.JobHuntRepository
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
    private val jobHuntRepository: JobHuntRepository,
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

    /** Guards link and unlink against a double tap. Separate from [isAnalyzing] because these
     * two run different calls and a student may legitimately do one while the other is in
     * flight -- sharing one flag would block a free action behind a paid one. */
    private val _isLinking = MutableStateFlow(false)
    val isLinking: StateFlow<Boolean> = _isLinking.asStateFlow()

    /**
     * The analysis key. One identity only -- the goal id is fixed for this screen and is the
     * whole of what the server fingerprints -- so this reads as a plain key/consume pair.
     *
     * It matters more here than anywhere else in this feature: analysis is the one call that
     * spends an AI generation and one of the student's hourly allowance, so a reply lost after
     * the server had already committed would otherwise cost them a second one.
     */
    private val analyzeKeys = OperationKeyHolder()

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
                repository.analyzeGoal(careerGoalId, analyzeKeys.keyFor())
                analyzeKeys.consume()
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

    /**
     * Attach a tracked application to this goal, then re-read the goal from the server.
     *
     * Re-reads rather than patching the state locally from the link response, and that is
     * deliberate: the link call returns the list row, which carries only the application's
     * status, while the screen shows the whole application block. Rendering a half-populated
     * block from a partial response would show the student a state the server never described.
     *
     * The failure path never fabricates a link. If the server refuses -- someone else's job
     * (404), already claimed by another goal (409), no network -- the goal on screen stays
     * exactly as it was and the reason appears beside it.
     */
    fun linkApplication(savedJobId: Int) {
        if (_isLinking.value) return
        viewModelScope.launch {
            _isLinking.value = true
            _analysisError.value = null
            try {
                repository.linkApplication(careerGoalId, savedJobId)
                _uiState.value = CareerGoalDetailUiState.Success(repository.getGoal(careerGoalId))
                _linkableJobs.value = null
            } catch (e: Exception) {
                _analysisError.value = e.toUserMessage("link this application")
            } finally {
                _isLinking.value = false
            }
        }
    }

    fun unlinkApplication() {
        if (_isLinking.value) return
        viewModelScope.launch {
            _isLinking.value = true
            _analysisError.value = null
            try {
                repository.unlinkApplication(careerGoalId)
                _uiState.value = CareerGoalDetailUiState.Success(repository.getGoal(careerGoalId))
            } catch (e: Exception) {
                _analysisError.value = e.toUserMessage("unlink this application")
            } finally {
                _isLinking.value = false
            }
        }
    }

    /**
     * The student's own tracked jobs, for choosing which one this goal is about.
     *
     * Read on demand rather than with the goal: most visits to this screen are not about
     * linking, and fetching everyone's job list on every open would make the common case pay
     * for the rare one. Nothing is filtered out by status -- a job already at interview stage
     * is a perfectly ordinary thing to link a goal to, and hiding it would leave the student
     * unable to connect the two.
     */
    private val _linkableJobs = MutableStateFlow<List<SavedJobDto>?>(null)
    val linkableJobs: StateFlow<List<SavedJobDto>?> = _linkableJobs.asStateFlow()

    private val _isLoadingLinkable = MutableStateFlow(false)
    val isLoadingLinkable: StateFlow<Boolean> = _isLoadingLinkable.asStateFlow()

    fun loadLinkableJobs() {
        if (_isLoadingLinkable.value) return
        viewModelScope.launch {
            _isLoadingLinkable.value = true
            _analysisError.value = null
            try {
                _linkableJobs.value = jobHuntRepository.listSaved()
            } catch (e: Exception) {
                // An empty list would read as "you have no saved jobs", which is a claim about
                // the student's tracker rather than about this request. Stay null and say why.
                _linkableJobs.value = null
                _analysisError.value = e.toUserMessage("load your tracked jobs")
            } finally {
                _isLoadingLinkable.value = false
            }
        }
    }

    fun clearLinkableJobs() {
        _linkableJobs.value = null
    }
}
