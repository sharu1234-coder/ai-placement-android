package com.aiplacementos.app.ui.jobhunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.SavedSearchDto
import com.aiplacementos.app.domain.repository.JobHuntRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SavedSearchesUiState {
    data object Loading : SavedSearchesUiState

    data class Failed(val failure: JobSearchFailure) : SavedSearchesUiState

    data class Success(
        val searches: List<SavedSearchDto>,
        /** Ids currently being deleted, so one row disables its own control rather than the
         * whole list going inert. */
        val deletingIds: Set<Int> = emptySet(),
        val actionError: String? = null,
        /** Set once a save succeeds, so the screen can confirm without inventing a count. */
        val justSavedName: String? = null,
    ) : SavedSearchesUiState {
        val isEmpty: Boolean get() = searches.isEmpty()
    }
}

/**
 * Saved searches: name a set of filters and re-run it later.
 *
 * The list is free, and stays free — the Premium boundary is on the advanced *filters*, not on
 * storing them. That means a saved search can legitimately contain filters its owner can no
 * longer run: they could have saved it while subscribed, or the boundary could move. Re-running
 * one therefore goes through the same search endpoint as anything else and gets the same
 * server-side answer, rather than this screen trying to predict entitlement locally.
 */
class SavedSearchesViewModel(private val jobHuntRepository: JobHuntRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<SavedSearchesUiState>(SavedSearchesUiState.Loading)
    val uiState: StateFlow<SavedSearchesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SavedSearchesUiState.Loading
            try {
                _uiState.value = SavedSearchesUiState.Success(jobHuntRepository.listSavedSearches())
            } catch (e: Exception) {
                _uiState.value = SavedSearchesUiState.Failed(e.toJobSearchFailure())
            }
        }
    }

    fun retry() = load()

    fun save(name: String, filters: JobFilters) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                jobHuntRepository.createSavedSearch(trimmed, filters.toRequest())
                val refreshed = jobHuntRepository.listSavedSearches()
                _uiState.value = SavedSearchesUiState.Success(refreshed, justSavedName = trimmed)
            } catch (e: Exception) {
                val current = _uiState.value
                val message = e.toJobSearchFailure().message
                _uiState.value = if (current is SavedSearchesUiState.Success) {
                    // A failed save must not discard the list the student is looking at.
                    current.copy(actionError = message)
                } else {
                    SavedSearchesUiState.Failed(e.toJobSearchFailure())
                }
            }
        }
    }

    fun delete(id: Int) {
        val current = _uiState.value as? SavedSearchesUiState.Success ?: return
        if (id in current.deletingIds) return
        viewModelScope.launch {
            _uiState.value = current.copy(deletingIds = current.deletingIds + id, actionError = null)
            try {
                jobHuntRepository.deleteSavedSearch(id)
                val after = _uiState.value as? SavedSearchesUiState.Success ?: return@launch
                _uiState.value = after.copy(
                    searches = after.searches.filterNot { it.id == id },
                    deletingIds = after.deletingIds - id,
                )
            } catch (e: Exception) {
                val after = _uiState.value as? SavedSearchesUiState.Success ?: return@launch
                _uiState.value = after.copy(
                    deletingIds = after.deletingIds - id,
                    actionError = e.toJobSearchFailure().message,
                )
            }
        }
    }

    fun clearActionError() {
        val current = _uiState.value as? SavedSearchesUiState.Success ?: return
        _uiState.value = current.copy(actionError = null, justSavedName = null)
    }
}

/**
 * A one-line description of what a saved search actually looks for.
 *
 * Built only from filters that are set. An unset filter contributes nothing rather than a
 * word like "Anywhere" — the search did not say "anywhere", it said nothing, and printing a
 * value the student never chose is how a saved search stops meaning what they saved.
 */
fun savedSearchSummary(search: SavedSearchDto): String {
    val filters = search.filters
    val parts = listOfNotNull(
        filters.keywords?.takeIf { it.isNotBlank() },
        filters.location?.takeIf { it.isNotBlank() },
        filters.workMode?.let { workModeLabel(it) },
        filters.employmentType?.let { employmentTypeLabel(it) },
        filters.experienceLevel?.let { seniorityLabel(it) },
        filters.salaryMin?.let { "₹$it+" },
        filters.maxDaysOld?.let { days ->
            when (days) {
                1 -> "Posted today"
                7 -> "Posted this week"
                30 -> "Posted this month"
                else -> "Posted in $days days"
            }
        },
    )
    return if (parts.isEmpty()) "No filters — every job we can find" else parts.joinToString(" · ")
}

/**
 * What to show for "last run". Null when it has never run — the screen must not render that
 * as zero results, which would say we searched and found nothing.
 */
fun savedSearchLastRunLabel(search: SavedSearchDto): String? {
    if (search.lastRunAt == null) return null
    val count = search.lastResultCount ?: return "Run once already"
    return if (count == 1) "1 job last time" else "$count jobs last time"
}
