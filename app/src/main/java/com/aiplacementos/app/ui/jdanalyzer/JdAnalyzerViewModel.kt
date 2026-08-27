package com.aiplacementos.app.ui.jdanalyzer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.JobDescriptionListItemDto
import com.aiplacementos.app.domain.repository.JobRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Two independent async operations on this screen -- loading past analyses (a list) and
 * analyzing a newly-pasted JD (a one-shot action with its own busy/error state) -- so they're
 * modeled as separate StateFlows rather than one UiState<T>, the same reasoning
 * RoadmapUiState/PlacementPathUiState document for their own screens.
 */
class JdAnalyzerViewModel(private val jobRepository: JobRepository) : ViewModel() {
    private val _pastAnalyses = MutableStateFlow<UiState<List<JobDescriptionListItemDto>>>(UiState.Loading)
    val pastAnalyses: StateFlow<UiState<List<JobDescriptionListItemDto>>> = _pastAnalyses.asStateFlow()

    private val _analyzing = MutableStateFlow(false)
    val analyzing: StateFlow<Boolean> = _analyzing.asStateFlow()

    private val _analyzeError = MutableStateFlow<String?>(null)
    val analyzeError: StateFlow<String?> = _analyzeError.asStateFlow()

    /** No init{} auto-load here -- this screen's own composable re-triggers loadPastAnalyses()
     * on every resume (via LifecycleResumeEffect), including first appearance, so a saved/
     * deleted analysis on the result screen is reflected the moment the user comes back
     * rather than only after a fresh process/composable recreation. */
    fun loadPastAnalyses() {
        viewModelScope.launch {
            _pastAnalyses.value = UiState.Loading
            try {
                val analyses = jobRepository.listJobDescriptions()
                _pastAnalyses.value = if (analyses.isEmpty()) UiState.Empty else UiState.Success(analyses)
            } catch (e: Exception) {
                _pastAnalyses.value = UiState.Error("Couldn't load your past analyses. Check your connection.")
            }
        }
    }

    /** Runs the full pipeline (parse -> generate preparation plan) so the result screen
     * always has something real to show the moment it opens, rather than needing its own
     * follow-up generation call for a company/job title it wasn't passed. */
    fun analyze(rawText: String, companyName: String, jobTitle: String, onAnalyzed: (jdId: Int) -> Unit) {
        viewModelScope.launch {
            _analyzing.value = true
            _analyzeError.value = null
            try {
                val jd = jobRepository.parseJobDescription(rawText)
                jobRepository.generatePreparationPlan(
                    jd.id,
                    companyName.trim().ifBlank { null },
                    jobTitle.trim().ifBlank { null },
                )
                onAnalyzed(jd.id)
            } catch (e: HttpException) {
                _analyzeError.value = if (e.code() == 422) {
                    "Upload and complete your resume first, then analyze this job description."
                } else {
                    "Couldn't analyze that job description (${e.code()})."
                }
            } catch (e: Exception) {
                _analyzeError.value = "Couldn't analyze that job description. Check your connection and try again."
            } finally {
                _analyzing.value = false
            }
        }
    }

    fun deleteAnalysis(jdId: Int) {
        viewModelScope.launch {
            try {
                jobRepository.deleteJobDescription(jdId)
                loadPastAnalyses()
            } catch (e: Exception) {
                // A single delete failing isn't worth a full error screen -- the item just
                // stays in the list, and the user can retry the swipe/tap.
            }
        }
    }
}
