package com.aiplacementos.app.ui.jobhunt

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.JobResultDto
import com.aiplacementos.app.data.remote.JobSearchRequestDto
import com.aiplacementos.app.domain.repository.JobHuntRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
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
    /**
     * The saved search this screen was opened to run, if it was.
     *
     * Set means the first search goes through POST /saved-searches/{id}/run rather than
     * replaying the filters through POST /search. The two would return the same jobs, but only
     * the former is the server's record that the search ran -- it is what writes last_run_at,
     * last_success_at and last_result_count. Replaying the filters left those permanently NULL
     * however many times a student re-ran a saved search, so the screen that shows them could
     * only ever say "never run".
     *
     * [initialFilters] still travels alongside it, and is not redundant: it seeds the filter
     * sheet and builds the request for page two onward, neither of which the run endpoint
     * covers.
     */
    savedSearchId: Int? = null,
    /** Backs the idempotency key so it survives process death. Optional so tests and previews
     * can construct this without one; a null handle simply means the key lives in memory, and
     * the server behaves correctly either way. */
    private val savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<JobSearchUiState>(JobSearchUiState.Loading)
    val uiState: StateFlow<JobSearchUiState> = _uiState.asStateFlow()

    private var activeTab: JobFilterTab = JobFilterTab.All
    private var filters: JobFilters = initialFilters

    /**
     * The saved search still being run, or null once this is an ordinary search.
     *
     * Cleared the moment the student changes a tab or a filter. At that point they are no
     * longer running the thing they saved -- they are searching for something else that
     * happens to have started from it -- and recording that as a run of the saved search would
     * put a result count against a set of filters it was never run with.
     */
    private var runningSavedSearchId: Int? = savedSearchId

    /**
     * The id of the search operation currently on screen.
     *
     * One tap on "Find Jobs" is one operation, and everything that follows from it -- the first
     * request, a retry after a dropped connection, the next page -- carries this same id, so the
     * server charges the student once for the thing they did once. Choosing a *new* tab or
     * applying different filters is a different thing to have done, so it mints a new id.
     *
     * Kept in SavedStateHandle rather than a plain field because the case it exists for is
     * precisely the one that destroys plain fields: Android killing the process mid-request and
     * the screen re-running the same search on the way back. Without this, a free student who
     * lost their connection at the wrong moment would come back to "your free search is used"
     * having never seen a result.
     *
     * It is a convenience, never an authorisation. The server binds it to the search parameters
     * and to the caller's own profile, so the worst a wrong id can do is fail to deduplicate.
     */
    private var operationId: String
        get() {
            savedStateHandle?.get<String>(OPERATION_ID_KEY)?.let { return it }
            return inMemoryOperationId ?: newOperationId()
        }
        set(value) {
            inMemoryOperationId = value
            savedStateHandle?.set(OPERATION_ID_KEY, value)
        }

    private var inMemoryOperationId: String? = null

    private fun newOperationId(): String =
        // Prefixed so a key is recognisable in a server log, and trimmed to stay inside the
        // 64-character limit the server enforces.
        "and-${UUID.randomUUID()}".take(64).also { operationId = it }

    init {
        // A fresh id for the search this screen opens with -- unless the process was killed and
        // is coming back, in which case the stored one is reused and the trip does not cost a
        // second search.
        if (savedStateHandle?.get<String>(OPERATION_ID_KEY) == null) newOperationId()
        search()
    }

    fun onTabSelected(tab: JobFilterTab) {
        if (tab == activeTab) return
        activeTab = tab
        runningSavedSearchId = null
        // A different tab is a different search, so it is a different operation. Reusing the id
        // would make the server refuse it as a conflicting replay rather than run it.
        newOperationId()
        search()
    }

    fun applyFilters(newFilters: JobFilters) {
        filters = newFilters
        runningSavedSearchId = null
        newOperationId()
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
        runningSavedSearchId = null
        newOperationId()
        search()
    }

    /**
     * Re-run the search that just failed, as the same operation.
     *
     * The id is deliberately *not* regenerated. A retry after "the job service isn't
     * responding" is the student asking for the same thing again, and the server treats a
     * previously failed operation as still owed -- so this costs nothing extra. Minting a new
     * id here would turn every tap of a Retry button into a fresh search.
     *
     * A retry of a saved-search run is still that run, so it goes back through the same
     * endpoint -- which is also what lets the failed attempt's last_error_at be replaced by a
     * real last_success_at rather than left standing.
     */
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
                // Same operation id: a page is part of the search the student asked for, not a
                // new one. The server excludes page and page_size from the fingerprint for
                // exactly this reason.
                val response = jobHuntRepository.search(
                    effectiveRequest().copy(page = nextPage),
                    idempotencyKey = operationId,
                )
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
                // One call either way. The saved-search branch is not an extra request on top
                // of a search -- it is the same search, asked for by the name the student gave
                // it, so that the server records the run it actually performed.
                val savedSearchId = runningSavedSearchId
                val response = if (savedSearchId != null) {
                    jobHuntRepository.runSavedSearch(savedSearchId, operationId)
                } else {
                    jobHuntRepository.search(effectiveRequest(), operationId)
                }
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
        const val OPERATION_ID_KEY = "job_hunt_operation_id"
    }

    fun clearActionError() = updateSuccess { it.copy(actionError = null) }

    /** Re-reads the latest state before mutating, so a save completing after a tab change
     * cannot resurrect the previous tab's result list. */
    private fun updateSuccess(transform: (JobSearchUiState.Success) -> JobSearchUiState.Success) {
        val latest = _uiState.value as? JobSearchUiState.Success ?: return
        _uiState.value = transform(latest)
    }
}
