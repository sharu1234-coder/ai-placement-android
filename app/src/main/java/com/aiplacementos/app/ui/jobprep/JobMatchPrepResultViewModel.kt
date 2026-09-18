package com.aiplacementos.app.ui.jobprep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.JobPreparationPlanDto
import com.aiplacementos.app.domain.repository.AssessmentRepository
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.domain.repository.JobRepository
import com.aiplacementos.app.domain.repository.RoadmapRepository
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** A results screen has more sub-phases than UiState<T> can model -- a busy roadmap generation
 * must keep the plan on screen rather than flashing a blank loading state. Same deliberate
 * exception as RoadmapUiState/PlacementPathUiState. */
sealed interface JobMatchPrepResultUiState {
    data object Loading : JobMatchPrepResultUiState
    data class Error(val message: String) : JobMatchPrepResultUiState
    data class Success(
        val plan: JobPreparationPlanDto,
        val isGeneratingRoadmap: Boolean = false,
        val isUpdatingCareerPath: Boolean = false,
        val isStartingPractice: Boolean = false,
        val actionError: String? = null,
    ) : JobMatchPrepResultUiState
}

/**
 * The result half of Job Match & Prep.
 *
 * Absorbs both halves of what used to be two features: the analyzer's plan, roadmap and
 * career-path actions, and the old Job Preparation flow's handoff into the real assessment
 * engine. Neither engine is reimplemented here -- this only starts them.
 */
class JobMatchPrepResultViewModel(
    private val jdId: Int,
    private val jobRepository: JobRepository,
    private val roadmapRepository: RoadmapRepository,
    private val assessmentRepository: AssessmentRepository,
    // Career Path is an independent feature; this is the one integration point the product
    // calls for ("fold this job into my Career Path"), reached only through its public API.
    private val careerPathRepository: CareerPathRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<JobMatchPrepResultUiState>(JobMatchPrepResultUiState.Loading)
    val uiState: StateFlow<JobMatchPrepResultUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /**
     * Reads the saved plan, generating one only if none exists.
     *
     * The order matters: GET first, POST only on a 404. Generating on every open would spend
     * an AI call -- and one of the student's hourly allowance -- every time they reopened a
     * saved analysis or rotated the screen.
     */
    fun load() {
        viewModelScope.launch {
            _uiState.value = JobMatchPrepResultUiState.Loading
            try {
                val plan = try {
                    jobRepository.getPreparationPlan(jdId)
                } catch (e: HttpException) {
                    // Reopening an analysis whose plan generation never finished, or one saved
                    // before plans existed at all.
                    if (e.code() == 404) jobRepository.generatePreparationPlan(jdId, null, null) else throw e
                }
                _uiState.value = JobMatchPrepResultUiState.Success(plan)
            } catch (e: Exception) {
                // Deliberately no 422 branch any more: the server used to refuse to build a
                // plan without a resume, and now returns one that names what it could not
                // assess instead. A 422 here would be a genuine server problem, not a
                // missing resume, so it is classified like any other failure.
                _uiState.value = JobMatchPrepResultUiState.Error(e.toUserMessage("load this analysis"))
            }
        }
    }

    /** [onReady] fires only once the roadmap genuinely exists server-side -- not a
     * StateFlow-driven navigation event, which this codebase deliberately avoids. */
    fun generateRoadmap(durationDays: Int, onReady: () -> Unit) {
        val current = _uiState.value as? JobMatchPrepResultUiState.Success ?: return
        if (current.isGeneratingRoadmap) return
        viewModelScope.launch {
            _uiState.value = current.copy(isGeneratingRoadmap = true, actionError = null)
            try {
                roadmapRepository.generateRoadmap(jdId, durationDays)
                onReady()
            } catch (e: Exception) {
                _uiState.value = current.copy(
                    isGeneratingRoadmap = false,
                    actionError = e.toUserMessage("build your preparation plan"),
                )
            }
        }
    }

    /**
     * Starts practice for this job, using the existing assessment engine.
     *
     * This is the old Job Preparation flow's one genuinely useful step, carried over: a
     * blueprint built from THIS job's requirements, handed to the same test flow every other
     * practice entry point uses.
     */
    fun startPractice(onReady: (blueprintId: Int) -> Unit) {
        val current = _uiState.value as? JobMatchPrepResultUiState.Success ?: return
        if (current.isStartingPractice) return
        viewModelScope.launch {
            _uiState.value = current.copy(isStartingPractice = true, actionError = null)
            try {
                val blueprint = assessmentRepository.createBlueprint(roleId = null, jdId = jdId)
                onReady(blueprint.id)
                _uiState.value = current.copy(isStartingPractice = false)
            } catch (e: Exception) {
                _uiState.value = current.copy(
                    isStartingPractice = false,
                    actionError = e.toUserMessage("start practice for this job"),
                )
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                jobRepository.deleteJobDescription(jdId)
                onDeleted()
            } catch (e: Exception) {
                val current = _uiState.value as? JobMatchPrepResultUiState.Success ?: return@launch
                _uiState.value = current.copy(actionError = e.toUserMessage("delete this analysis"))
            }
        }
    }

    /** Merges this job's requirements into the student's current Career Path. With none
     * selected, says so rather than silently doing nothing or guessing a role. */
    fun updateCareerPath(onDone: (pathId: Int) -> Unit) {
        val current = _uiState.value as? JobMatchPrepResultUiState.Success ?: return
        if (current.isUpdatingCareerPath) return
        viewModelScope.launch {
            _uiState.value = current.copy(isUpdatingCareerPath = true, actionError = null)
            try {
                val currentPath = careerPathRepository.getCurrent()
                if (currentPath == null) {
                    _uiState.value = current.copy(
                        isUpdatingCareerPath = false,
                        actionError = "Select a Career Path first, then update it with this job.",
                    )
                    return@launch
                }
                careerPathRepository.applyJd(currentPath.id, jdId)
                onDone(currentPath.id)
            } catch (e: Exception) {
                _uiState.value = current.copy(
                    isUpdatingCareerPath = false,
                    actionError = e.toUserMessage("update your Career Path"),
                )
            }
        }
    }

    fun clearActionError() {
        val current = _uiState.value as? JobMatchPrepResultUiState.Success ?: return
        _uiState.value = current.copy(actionError = null)
    }
}
