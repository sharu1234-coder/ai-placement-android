package com.aiplacementos.app.ui.jobhunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ApplicationReadinessDto
import com.aiplacementos.app.domain.repository.JobHuntRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReadinessUiState {
    data object Loading : ReadinessUiState

    /**
     * Separate from [Failed] on purpose. A free student hitting this screen has not
     * encountered an error -- they have encountered a price -- and rendering the two the same
     * way would either make a paywall look broken or make a real outage look like an upsell.
     */
    data class PaywallRequired(val message: String) : ReadinessUiState

    data class Failed(val failure: JobSearchFailure) : ReadinessUiState

    data class Success(val readiness: ApplicationReadinessDto) : ReadinessUiState
}

class ApplicationReadinessViewModel(
    private val jobHuntRepository: JobHuntRepository,
    private val listingId: Int,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ReadinessUiState>(ReadinessUiState.Loading)
    val uiState: StateFlow<ReadinessUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ReadinessUiState.Loading
            try {
                _uiState.value = ReadinessUiState.Success(jobHuntRepository.getReadiness(listingId))
            } catch (e: Exception) {
                // The server's 403 carries a message naming what stays free. Surfacing it
                // verbatim is better than a generic "upgrade" line, because it tells the
                // student what they are *not* losing.
                val failure = e.toJobSearchFailure()
                _uiState.value = if (failure is JobSearchFailure.PremiumRequired) {
                    ReadinessUiState.PaywallRequired(failure.message)
                } else {
                    ReadinessUiState.Failed(failure)
                }
            }
        }
    }

    fun retry() = load()
}

/**
 * The headline band, in the student's language.
 *
 * There is deliberately no "Not ready". A low readiness means there is preparation worth doing,
 * not that the student should not apply -- adverts routinely over-state their requirements, and
 * that call is theirs to make, not ours.
 */
fun readinessBandLabel(band: String?): String? = when (band) {
    "strong_candidate" -> "You're a strong candidate!"
    "worth_applying" -> "Worth applying"
    "stretch" -> "A stretch, but possible"
    "prepare_first" -> "Worth preparing first"
    else -> null
}

/** Percentage for one dimension, or null when the server could not judge it. */
fun dimensionPercent(score: Float?): Int? = score?.let { (it * 100).toInt().coerceIn(0, 100) }

/**
 * What to show in place of a bar we have no number for.
 *
 * Never "0%". A student with no mock interviews has not failed interviews -- they have not
 * attempted one, and an empty bar labelled 0% says the opposite of the truth in the one place
 * a student is most likely to give up.
 */
fun unjudgedDimensionLabel(): String = "Not checked yet"

/** Where "Fix This Gap" should navigate, given the server's action key. */
enum class ReadinessGapDestination { Roadmap, SkillPassport, Resume, Interview, Profile }

fun gapDestination(action: String): ReadinessGapDestination = when (action) {
    "skill_passport" -> ReadinessGapDestination.SkillPassport
    "resume" -> ReadinessGapDestination.Resume
    "interview" -> ReadinessGapDestination.Interview
    "education" -> ReadinessGapDestination.Profile
    // "roadmap", and anything a newer backend introduces. The Roadmap is the one destination
    // that can absorb an unknown gap without misleading the student about what they will find.
    else -> ReadinessGapDestination.Roadmap
}
