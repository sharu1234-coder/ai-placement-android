package com.aiplacementos.app.ui.jdanalyzer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.JobPreparationPlanDto
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.domain.repository.JobRepository
import com.aiplacementos.app.domain.repository.RoadmapRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** A results screen has more sub-phases than UiState<T> can model (a busy roadmap-generation
 * action must keep showing the current plan, not flash a blank loading screen) -- same
 * deliberate exception as RoadmapUiState/PlacementPathUiState. */
sealed interface JdAnalyzerResultUiState {
    data object Loading : JdAnalyzerResultUiState
    data class Error(val message: String) : JdAnalyzerResultUiState
    data class Success(
        val plan: JobPreparationPlanDto,
        val isGeneratingRoadmap: Boolean = false,
        val isUpdatingCareerPath: Boolean = false,
        val careerPathUpdateError: String? = null,
    ) : JdAnalyzerResultUiState
}

class JdAnalyzerResultViewModel(
    private val jdId: Int,
    private val jobRepository: JobRepository,
    private val roadmapRepository: RoadmapRepository,
    // Career Path is a fully independent feature (see ui/careerpath/ package docs) -- this
    // is the ONE deliberate integration point the product spec calls for ("Update my Career
    // Path using this JD"), reached only through this repository's public API, never by
    // reading/writing placement_path-style internals.
    private val careerPathRepository: CareerPathRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<JdAnalyzerResultUiState>(JdAnalyzerResultUiState.Loading)
    val uiState: StateFlow<JdAnalyzerResultUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = JdAnalyzerResultUiState.Loading
            try {
                val plan = try {
                    jobRepository.getPreparationPlan(jdId)
                } catch (e: HttpException) {
                    // Reopening a saved analysis whose plan generation never completed (or a
                    // pre-generation-feature JD) -- fall back to generating one now, same
                    // "auto-generate if missing" contract RoadmapScreen already uses.
                    if (e.code() == 404) jobRepository.generatePreparationPlan(jdId, null, null) else throw e
                }
                _uiState.value = JdAnalyzerResultUiState.Success(plan)
            } catch (e: HttpException) {
                _uiState.value = JdAnalyzerResultUiState.Error(
                    if (e.code() == 422) {
                        "Upload and complete your resume first, then try analyzing this job again."
                    } else {
                        "Couldn't load this analysis (${e.code()})."
                    }
                )
            } catch (e: Exception) {
                _uiState.value = JdAnalyzerResultUiState.Error("Couldn't reach the server. Check your connection.")
            }
        }
    }

    /** [onReady] is a callback invoked only after the roadmap genuinely exists server-side --
     * not a StateFlow-driven one-shot navigation event, which this codebase deliberately
     * avoids after a prior LaunchedEffect(state)-based navigation trap (see
     * PlacementPathTopicDetailViewModel's practice()). */
    fun generateRoadmap(durationDays: Int, onReady: () -> Unit) {
        val current = _uiState.value as? JdAnalyzerResultUiState.Success ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(isGeneratingRoadmap = true)
            try {
                roadmapRepository.generateRoadmap(jdId, durationDays)
                onReady()
            } catch (e: Exception) {
                _uiState.value = current.copy(isGeneratingRoadmap = false)
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                jobRepository.deleteJobDescription(jdId)
                onDeleted()
            } catch (e: Exception) {
                // Leaves the current state as-is -- the user can retry the delete.
            }
        }
    }

    /** Merges this JD's requirements into the student's CURRENT Career Path (never
     * Placement Path/Roadmap) -- if none is selected yet, surfaces a clear message rather
     * than silently doing nothing or guessing which role to create one for. */
    fun updateCareerPath(onDone: (pathId: Int) -> Unit) {
        val current = _uiState.value as? JdAnalyzerResultUiState.Success ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(isUpdatingCareerPath = true, careerPathUpdateError = null)
            try {
                val currentPath = careerPathRepository.getCurrent()
                if (currentPath == null) {
                    _uiState.value = current.copy(
                        isUpdatingCareerPath = false,
                        careerPathUpdateError = "Select a Career Path first, then try updating it with this JD.",
                    )
                    return@launch
                }
                careerPathRepository.applyJd(currentPath.id, jdId)
                onDone(currentPath.id)
            } catch (e: Exception) {
                _uiState.value = current.copy(
                    isUpdatingCareerPath = false,
                    careerPathUpdateError = "Couldn't update your Career Path. Check your connection and try again.",
                )
            }
        }
    }
}
