package com.aiplacementos.app.ui.jobhunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.JobResultDto
import com.aiplacementos.app.data.remote.JobSearchRequestDto
import com.aiplacementos.app.domain.repository.JobHuntRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch

/** The quick filter chips across the top of the results screen. */
enum class JobFilterTab(val label: String) {
    All("All"),
    Remote("Remote"),
    FullTime("Full-time"),
    Internship("Internship"),
}

/**
 * The advanced filter set, held apart from the quick tabs.
 *
 * Three of these fields -- salary, experience level and date posted -- are Premium on the
 * server. They are still *offered* to a free student rather than hidden, because the server is
 * the only authority on entitlement (a client-side gate is a suggestion, not a control) and
 * because a paywall the student can see the shape of is more honest than one that hides what
 * it is selling. A free user who applies them gets the server's PremiumRequired response,
 * which the screen already renders as an upgrade prompt rather than an error.
 */
@Serializable
data class JobFilters(
    val keywords: String? = null,
    val location: String? = null,
    val workMode: String? = null,
    val employmentType: String? = null,
    val experienceLevel: String? = null,
    val salaryMin: Int? = null,
    val maxDaysOld: Int? = null,
) {
    /** Fields the backend gates behind Premium (app/api/job_hunt.py::_PREMIUM_FILTER_FIELDS).
     * Mirrored here only to label the controls; it is never used to block a request. */
    val usesPremiumFields: Boolean
        get() = salaryMin != null || employmentType != null || experienceLevel != null || maxDaysOld != null

    /** How many filters are actually set, for the badge on the filter button. Counts what the
     * student chose, so an unset field never inflates it. */
    val activeCount: Int
        get() = listOfNotNull(
            keywords?.takeIf { it.isNotBlank() },
            location?.takeIf { it.isNotBlank() },
            workMode,
            employmentType,
            experienceLevel,
            salaryMin,
            maxDaysOld,
        ).size

    fun toRequest(): JobSearchRequestDto = JobSearchRequestDto(
        keywords = keywords?.takeIf { it.isNotBlank() },
        location = location?.takeIf { it.isNotBlank() },
        workMode = workMode,
        employmentType = employmentType,
        experienceLevel = experienceLevel,
        salaryMin = salaryMin,
        maxDaysOld = maxDaysOld,
    )
}

/**
 * The inverse of [JobFilters.toRequest], for replaying a saved search.
 *
 * Page and page size are deliberately dropped: they describe one fetch, not what the student
 * was looking for, and restoring "page 4" from a saved search would open on results whose
 * first three pages they never saw.
 */
fun JobSearchRequestDto.toFilters(): JobFilters = JobFilters(
    keywords = keywords,
    location = location,
    workMode = workMode,
    employmentType = employmentType,
    experienceLevel = experienceLevel,
    salaryMin = salaryMin,
    maxDaysOld = maxDaysOld,
)

sealed interface JobSearchUiState {
    data object Loading : JobSearchUiState

    /** Carries the classified failure so the screen can decide whether to offer a retry, an
     * upgrade prompt, or the setup flow -- rather than showing one generic error for all. */
    data class Failed(val failure: JobSearchFailure) : JobSearchUiState

    data class Success(
        val results: List<JobResultDto>,
        val totalAvailable: Int?,
        val attributionText: String,
        val attributionUrl: String?,
        val fetchedAt: String,
        val activeTab: JobFilterTab,
        /** The advanced filters currently applied, so the sheet reopens on what is in effect
         * rather than resetting to empty. */
        val filters: JobFilters = JobFilters(),
        /** Ids currently being saved, so each card can disable just its own control instead of
         * blocking the whole list. */
        val savingListingIds: Set<Int> = emptySet(),
        val savedListingIds: Map<Int, Int> = emptyMap(),
        /** The page currently loaded. Results from earlier pages are kept and appended to. */
        val page: Int = 1,
        val isLoadingMore: Boolean = false,
        /** True only when we have positive evidence another page exists -- see [canLoadMore]. */
        val hasMore: Boolean = false,
        /** Non-null when a save failed. Shown as a transient message; the list is untouched. */
        val actionError: String? = null,
    ) : JobSearchUiState {
        /** True when the search genuinely completed and matched nothing. Distinct from every
         * [Failed] state -- the screen must never show "no jobs found" for a failure. */
        val isEmpty: Boolean get() = results.isEmpty()

        val canLoadMore: Boolean get() = hasMore && !isLoadingMore
    }
}

class JobHuntSearchViewModel(
    private val jobHuntRepository: JobHuntRepository,
    /** Filters to open with, when the student arrived by re-running a saved search. Empty for
     * a plain visit, which is the same thing as "no filters applied". */
    initialFilters: JobFilters = JobFilters(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<JobSearchUiState>(JobSearchUiState.Loading)
    val uiState: StateFlow<JobSearchUiState> = _uiState.asStateFlow()

    private var activeTab: JobFilterTab = JobFilterTab.All
    private var filters: JobFilters = initialFilters

    init {
        search()
    }

    fun onTabSelected(tab: JobFilterTab) {
        if (tab == activeTab) return
        activeTab = tab
        search()
    }

    fun applyFilters(newFilters: JobFilters) {
        filters = newFilters
        // A work-mode or employment-type choice in the sheet is the same axis the quick tabs
        // control. Leaving a stale tab selected would show "Remote" highlighted while the
        // sheet searched for something else, so the tab resets to All and the sheet becomes
        // the single source of truth until the student picks a tab again.
        if (newFilters.workMode != null || newFilters.employmentType != null) {
            activeTab = JobFilterTab.All
        }
        search()
    }

    fun clearFilters() {
        filters = JobFilters()
        search()
    }

    fun retry() = search()

    /**
     * Fetch the next page and append it.
     *
     * Appending rather than replacing, and never re-sorting the accumulated list: the server
     * ranks within a page, and re-sorting across pages client-side would reorder rows the
     * student has already scrolled past. A duplicate listing id is dropped, because the same
     * advert can legitimately appear on two pages if the provider's own ordering shifts
     * between requests.
     */
    fun loadMore() {
        val current = _uiState.value as? JobSearchUiState.Success ?: return
        if (!current.canLoadMore) return

        viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMore = true, actionError = null)
            val nextPage = current.page + 1
            try {
                val response = jobHuntRepository.search(effectiveRequest().copy(page = nextPage))
                val seen = current.results.map { it.listing.id }.toSet()
                val appended = current.results + response.results.filterNot { it.listing.id in seen }
                _uiState.value = current.copy(
                    results = appended,
                    page = nextPage,
                    isLoadingMore = false,
                    hasMore = hasMoreAfter(nextPage, appended.size, response.totalAvailable, response.results.size),
                    savedListingIds = current.savedListingIds + response.results
                        .filter { it.savedJobId != null }
                        .associate { it.listing.id to it.savedJobId!! },
                )
            } catch (e: Exception) {
                // A failed page must not destroy the pages already on screen. The list stays,
                // and the failure is reported as a transient action error.
                _uiState.value = current.copy(
                    isLoadingMore = false,
                    actionError = e.toJobSearchFailure().message,
                )
            }
        }
    }

    private fun search() {
        viewModelScope.launch {
            _uiState.value = JobSearchUiState.Loading
            try {
                val response = jobHuntRepository.search(effectiveRequest())
                _uiState.value = JobSearchUiState.Success(
                    results = response.results,
                    totalAvailable = response.totalAvailable,
                    attributionText = response.attributionText,
                    attributionUrl = response.attributionUrl,
                    fetchedAt = response.fetchedAt,
                    activeTab = activeTab,
                    filters = filters,
                    savedListingIds = response.results
                        .filter { it.savedJobId != null }
                        .associate { it.listing.id to it.savedJobId!! },
                    page = 1,
                    hasMore = hasMoreAfter(1, response.results.size, response.totalAvailable, response.results.size),
                )
            } catch (e: Exception) {
                _uiState.value = JobSearchUiState.Failed(e.toJobSearchFailure())
            }
        }
    }

    /**
     * The quick tab and the advanced sheet, combined into one request.
     *
     * The tab wins on the one axis it owns. That is deliberate: the tab is a single visible
     * control and the sheet is behind a button, so if the two ever disagree the student can
     * see why the results look the way they do.
     *
     * "Full-time" and "Internship" map to `employment_type`, which the backend gates behind
     * Premium -- a free student selecting those gets a PremiumRequired state rather than
     * silently unfiltered results, which is the honest outcome even though it is the less
     * convenient one.
     */
    /**
     * Whether another page is worth asking for.
     *
     * Two independent signals, and both must be respected. A provider that reports a total
     * tells us directly; one that does not (totalAvailable is null, which several providers
     * genuinely are) leaves only the shape of the last response, so a short page is taken as
     * the end. Guessing "probably more" without either signal would show the student a Load
     * more button that always returns nothing.
     *
     * The hard page cap mirrors the backend's own (1..10) rather than being discovered by
     * getting a 422 back.
     */
    private fun hasMoreAfter(
        page: Int,
        loadedCount: Int,
        totalAvailable: Int?,
        lastPageSize: Int,
    ): Boolean {
        if (page >= MAX_PAGE) return false
        if (lastPageSize == 0) return false
        if (totalAvailable != null) return loadedCount < totalAvailable
        return lastPageSize >= DEFAULT_PAGE_SIZE
    }

    private fun effectiveRequest(): JobSearchRequestDto {
        val base = filters.toRequest()
        return when (activeTab) {
            JobFilterTab.All -> base
            JobFilterTab.Remote -> base.copy(workMode = "remote")
            JobFilterTab.FullTime -> base.copy(employmentType = "full_time")
            JobFilterTab.Internship -> base.copy(employmentType = "internship")
        }
    }

    fun toggleSave(listingId: Int) {
        val current = _uiState.value as? JobSearchUiState.Success ?: return
        if (listingId in current.savingListingIds) return

        viewModelScope.launch {
            _uiState.value = current.copy(
                savingListingIds = current.savingListingIds + listingId,
                actionError = null,
            )
            try {
                val existingSavedId = current.savedListingIds[listingId]
                if (existingSavedId != null) {
                    jobHuntRepository.deleteSaved(existingSavedId)
                    updateSuccess { state ->
                        state.copy(savedListingIds = state.savedListingIds - listingId)
                    }
                } else {
                    val saved = jobHuntRepository.saveJob(listingId)
                    updateSuccess { state ->
                        state.copy(savedListingIds = state.savedListingIds + (listingId to saved.id))
                    }
                }
            } catch (e: Exception) {
                updateSuccess { state ->
                    state.copy(actionError = e.toJobSearchFailure().message)
                }
            } finally {
                updateSuccess { state ->
                    state.copy(savingListingIds = state.savingListingIds - listingId)
                }
            }
        }
    }

    /**
     * Hide a job the student is not interested in.
     *
     * Recorded server-side as the "dismissed" status rather than only removed from this list,
     * so the decision survives a refresh and a reinstall. Removed from the visible results
     * immediately: a dismissal that leaves the card sitting there reads as a failed tap, and
     * the student taps again.
     */
    fun dismiss(listingId: Int) {
        val current = _uiState.value as? JobSearchUiState.Success ?: return
        if (listingId in current.savingListingIds) return

        viewModelScope.launch {
            _uiState.value = current.copy(savingListingIds = current.savingListingIds + listingId)
            try {
                jobHuntRepository.saveJob(listingId, status = "dismissed")
                val after = _uiState.value as? JobSearchUiState.Success ?: return@launch
                _uiState.value = after.copy(
                    results = after.results.filterNot { it.listing.id == listingId },
                    savingListingIds = after.savingListingIds - listingId,
                )
            } catch (e: Exception) {
                val after = _uiState.value as? JobSearchUiState.Success ?: return@launch
                // The card stays put on failure. Removing it locally after the server refused
                // would make the job reappear on the next search with no explanation.
                _uiState.value = after.copy(
                    savingListingIds = after.savingListingIds - listingId,
                    actionError = e.toJobSearchFailure().message,
                )
            }
        }
    }

    /**
     * Save the filters currently in effect under a name.
     *
     * Saves what the student can see is applied -- the sheet's filters plus whatever the quick
     * tab adds -- rather than the sheet alone, so re-running it reproduces this exact result
     * set rather than a subtly wider one.
     */
    fun saveCurrentSearch(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                jobHuntRepository.createSavedSearch(trimmed, effectiveRequest())
                updateSuccess { it.copy(actionError = "Saved \"$trimmed\"") }
            } catch (e: Exception) {
                updateSuccess { it.copy(actionError = e.toJobSearchFailure().message) }
            }
        }
    }

    private companion object {
        // Both mirror app/api/job_hunt.py's JobSearchRequest bounds, so the client stops at
        // the same place the server does instead of discovering it via a 422.
        const val MAX_PAGE = 10
        const val DEFAULT_PAGE_SIZE = 20
    }

    fun clearActionError() = updateSuccess { it.copy(actionError = null) }

    /** Re-reads the latest state before mutating, so a save completing after a tab change
     * cannot resurrect the previous tab's result list. */
    private fun updateSuccess(transform: (JobSearchUiState.Success) -> JobSearchUiState.Success) {
        val latest = _uiState.value as? JobSearchUiState.Success ?: return
        _uiState.value = transform(latest)
    }
}
