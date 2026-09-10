package com.aiplacementos.app.ui.jobhunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.JobHuntEntitlementDto
import com.aiplacementos.app.data.remote.JobHuntHomeDto
import com.aiplacementos.app.data.remote.JobSearchProfileDto
import com.aiplacementos.app.domain.repository.JobHuntRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface JobHuntHomeUiState {
    data object Loading : JobHuntHomeUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : JobHuntHomeUiState

    /**
     * [profile] always loads, even when empty -- an empty Job Hunt profile is a real state the
     * setup screen exists for, not an error and not an Empty variant. The screen branches on
     * [JobSearchProfileDto.isReadyToSearch] to decide between the setup prompt and the
     * matches feed.
     */
    data class Success(
        val profile: JobSearchProfileDto,
        /**
         * Counts and top recommendations. Null when the summary call failed on its own --
         * the profile still loaded, and a student whose tiles are missing must still be able
         * to reach search and setup. Degrading independently, the same pattern Home uses for
         * its seven other sources.
         */
        val home: JobHuntHomeDto? = null,
        /**
         * What the server says this student may do next, so the screen can say "one free
         * search" before the tap instead of after the refusal. Null when the call failed on
         * its own -- and null is rendered as *no claim at all* rather than as either answer,
         * because guessing "you still have a free search" would set up a refusal the student
         * was told would not come, and guessing the opposite would hide a search they own.
         */
        val entitlement: JobHuntEntitlementDto? = null,
    ) : JobHuntHomeUiState
}

class JobHuntHomeViewModel(private val jobHuntRepository: JobHuntRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<JobHuntHomeUiState>(JobHuntHomeUiState.Loading)
    val uiState: StateFlow<JobHuntHomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() = load()

    fun load() {
        viewModelScope.launch {
            _uiState.value = JobHuntHomeUiState.Loading
            try {
                val profile = jobHuntRepository.getProfile()
                // The summary is a nice-to-have on this screen; the profile is not. A failed
                // summary must never cost the student their way into setup or search.
                val home = runCatching { jobHuntRepository.getHome() }.getOrNull()
                // Same rule as the summary above: this is a label on a button, not a gate. The
                // server decides entitlement on the search request itself, so a student whose
                // entitlement call failed can still tap Find Jobs and get the right answer.
                val entitlement = runCatching { jobHuntRepository.getEntitlement() }.getOrNull()
                _uiState.value = JobHuntHomeUiState.Success(profile, home, entitlement)
            } catch (e: Exception) {
                _uiState.value = JobHuntHomeUiState.Error(
                    e.toUserMessage("load Job Hunt"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }
}
