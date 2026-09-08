package com.aiplacementos.app.ui.jobhunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.SkillMatchAnalysisDto
import com.aiplacementos.app.data.remote.SkillMatchRowDto
import com.aiplacementos.app.domain.repository.JobHuntRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SkillMatchUiState {
    data object Loading : SkillMatchUiState

    data class Failed(val failure: JobSearchFailure) : SkillMatchUiState

    data class Success(val analysis: SkillMatchAnalysisDto) : SkillMatchUiState
}

class SkillMatchAnalysisViewModel(
    private val jobHuntRepository: JobHuntRepository,
    private val listingId: Int,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SkillMatchUiState>(SkillMatchUiState.Loading)
    val uiState: StateFlow<SkillMatchUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SkillMatchUiState.Loading
            try {
                _uiState.value = SkillMatchUiState.Success(
                    jobHuntRepository.getSkillAnalysis(listingId),
                )
            } catch (e: Exception) {
                _uiState.value = SkillMatchUiState.Failed(e.toJobSearchFailure())
            }
        }
    }

    fun retry() = load()
}

/**
 * The badge label for one skill row.
 *
 * These are the recruiter's-eye view, deliberately distinct from the provenance headings on the
 * detail screen. "Match" for a resume-only skill is accurate on a CV screen even though our own
 * evidence for it is only a claim -- calling it "Weak" here would understate what the student
 * actually has, and calling it "Strong" would overstate what we can prove.
 */
fun matchStrengthLabel(strength: String): String = when (strength) {
    "strong_match" -> "Strong match"
    "match" -> "Match"
    "skill_gap" -> "Skill gap"
    "unclear" -> "Unclear"
    // An unrecognised value from a newer backend renders as nothing rather than as a raw enum.
    else -> ""
}

/**
 * The sub-line under a skill, saying where our confidence comes from.
 *
 * Short by design: the row already carries the skill and the badge, and this is the third
 * thing on one line. The server also sends a longer `detail`, which the row prefers when it
 * has one -- this is the fallback so a row is never left with a bare skill name.
 */
fun skillProvenanceCaption(status: String): String = when (status) {
    "verified_evidence" -> "Verified evidence"
    "on_resume" -> "On your resume"
    "missing" -> "Not found"
    "unclear" -> "We can't tell yet"
    else -> ""
}

/**
 * Rows in the order the analysis should read: what you can prove, then what you claim, then
 * what is missing, then what we could not judge.
 *
 * Sorted here rather than server-side because it is presentation: the API returns matched
 * skills before gaps, and this refines that into the four-way order the screen shows.
 */
fun sortedAnalysisRows(rows: List<SkillMatchRowDto>): List<SkillMatchRowDto> {
    val order = listOf("verified_evidence", "on_resume", "missing", "unclear")
    return rows.sortedBy { row ->
        // An unknown status sorts last rather than first, so a future bucket cannot push
        // itself to the top of a screen it was never designed for.
        order.indexOf(row.status).takeIf { it >= 0 } ?: order.size
    }
}
