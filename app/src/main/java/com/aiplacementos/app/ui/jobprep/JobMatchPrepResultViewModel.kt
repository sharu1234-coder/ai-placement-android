package com.aiplacementos.app.ui.jobprep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.JobPreparationPlanDto
import com.aiplacementos.app.domain.repository.AssessmentRepository
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.domain.repository.JobRepository
import com.aiplacementos.app.domain.repository.RoadmapRepository
import com.aiplacementos.app.ui.common.toUserMessage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
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

    /**
     * The Career Path to open once this job has been merged into it, until the screen opens it.
     *
     * State rather than the callback this used to take. The callback was captured when the button
     * was tapped, so it held the NavController of whichever composition was current then -- and a
     * configuration change replaces both. Navigating with the dead one throws, inside the request's
     * own try block, which reported a successful update as "Couldn't reach the server" (the same
     * class of failure Phase 14 found on the role-selection screen). Whichever composition is
     * current reads this instead, and navigating happens outside the network try/catch entirely.
     */
    private val _updatedCareerPathId = MutableStateFlow<Int?>(null)
    val updatedCareerPathId: StateFlow<Int?> = _updatedCareerPathId.asStateFlow()

    /** The Career Path update currently in flight, if any. Only read to tell "still running" from
     * "finished and navigated away" -- see [onScreenResumed]. */
    private var careerPathUpdateJob: Job? = null

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
    fun updateCareerPath() {
        val current = _uiState.value as? JobMatchPrepResultUiState.Success ?: return
        // A second tap while one update is in flight would send a second POST for one intention
        // and spend two of the ten the backend allows an hour. Uses the isUpdatingCareerPath this
        // screen already shows rather than adding a second piece of state to hold a lock -- and
        // because the flag outlives the success (below), it also covers the taps that can land on
        // the outgoing screen while the Career Path is sliding in over it.
        if (current.isUpdatingCareerPath) return
        careerPathUpdateJob = viewModelScope.launch {
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
                // isUpdatingCareerPath stays true on purpose: the screen is about to leave, and
                // re-enabling the button here would both flicker its label over the outgoing
                // screen and re-open the window a duplicate tap could get through.
                // onScreenResumed retires it when the student comes back.
                _updatedCareerPathId.value = currentPath.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = current.copy(
                    isUpdatingCareerPath = false,
                    actionError = e.toUserMessage("update your Career Path"),
                )
            }
        }
    }

    /** The screen opened [updatedCareerPathId]; it must not be opened again. */
    fun onUpdatedCareerPathOpened() {
        _updatedCareerPathId.value = null
    }

    /**
     * The screen became visible again.
     *
     * A successful Career Path update deliberately does not clear isUpdatingCareerPath where it
     * happens -- see updateCareerPath. But nothing cleared it afterwards either, and NavGraph
     * navigates to the Career Path detail screen without popUpTo, which is what the product wants:
     * Back has to come back here. So this screen's back-stack entry -- and this view model with it
     * -- outlives the navigation. The observed result was a screen that returned on Back with
     * "Add to my Career Path" reading "Updating...", disabled, permanently, for an update that had
     * already succeeded; tapping it sent nothing (F12-04).
     *
     * Resuming is the right moment because it is the first point at which the screen is both
     * visible and no longer mid-transition. The job check is what keeps this honest: a student who
     * backgrounds the app while an update is genuinely still running and comes back must still see
     * "Updating...", and must still be unable to send a second one.
     *
     * Only isUpdatingCareerPath is retired. isGeneratingRoadmap needs no equivalent because its
     * destination pops this screen off the back stack, and isStartingPractice is cleared where it
     * finishes; the audit checked both and neither is this defect.
     */
    fun onScreenResumed() {
        val current = _uiState.value as? JobMatchPrepResultUiState.Success ?: return
        if (!current.isUpdatingCareerPath) return
        if (careerPathUpdateJob?.isActive == true) return
        _uiState.value = current.copy(isUpdatingCareerPath = false)
    }

    fun clearActionError() {
        val current = _uiState.value as? JobMatchPrepResultUiState.Success ?: return
        _uiState.value = current.copy(actionError = null)
    }
}
