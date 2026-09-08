package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.JobHuntMatchDto
import com.aiplacementos.app.data.remote.JobListingDto
import com.aiplacementos.app.data.remote.JobResultDto
import com.aiplacementos.app.data.remote.JobSearchResponseDto
import com.aiplacementos.app.data.remote.SavedJobDto
import com.aiplacementos.app.data.remote.SavedSearchDto
import com.aiplacementos.app.fakes.FakeJobHuntRepository
import com.aiplacementos.app.fakes.httpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

fun sampleListing(
    id: Int = 1,
    title: String = "Software Engineer",
    company: String? = "Acme",
    location: String? = "Bengaluru",
    postedAt: String? = "2026-09-01T09:00:00",
    isStale: Boolean = false,
) = JobListingDto(
    id = id,
    provider = "fake",
    url = "https://jobs.example.com/$id",
    title = title,
    company = company,
    location = location,
    postedAt = postedAt,
    fetchedAt = "2026-09-07T10:00:00",
    isStale = isStale,
)

fun sampleMatch(
    totalScore: Float = 0.9f,
    matchedSkills: List<String> = listOf("python"),
    missingSkills: List<String> = emptyList(),
    unknownFactors: List<String> = emptyList(),
) = JobHuntMatchDto(
    totalScore = totalScore,
    scoreVersion = "v1",
    matchedSkills = matchedSkills,
    missingSkills = missingSkills,
    unknownFactors = unknownFactors,
    rationale = "You already have the skills this role asks for.",
    reasons = listOf("This is a software engineer role, which is what you're targeting"),
)

fun sampleResult(id: Int = 1, savedJobId: Int? = null) = JobResultDto(
    listing = sampleListing(id = id),
    match = sampleMatch(),
    savedJobId = savedJobId,
    savedStatus = if (savedJobId != null) "saved" else null,
)

private fun response(results: List<JobResultDto>, total: Int? = 10) = JobSearchResponseDto(
    results = results,
    totalAvailable = total,
    provider = "fake",
    attributionText = "Jobs by Fake",
    fetchedAt = "2026-09-07T10:00:00",
    scoreVersion = "v1",
)

private fun savedJob(id: Int = 99) = SavedJobDto(
    id = id,
    status = "saved",
    createdAt = "2026-09-07T10:00:00",
    updatedAt = "2026-09-07T10:00:00",
    listing = sampleListing(),
)

class JobHuntSearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows results on a successful search`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { response(listOf(sampleResult())) }
        }
        val state = JobHuntSearchViewModel(repository).uiState.value

        assertTrue(state is JobSearchUiState.Success)
        state as JobSearchUiState.Success
        assertEquals(1, state.results.size)
        assertEquals("Jobs by Fake", state.attributionText)
        assertEquals(10, state.totalAvailable)
    }

    @Test
    fun `an empty result set is a real success, never an error`() {
        // The distinction the whole feature turns on: "we searched and found nothing" must not
        // be rendered the same way as "we could not search".
        val repository = FakeJobHuntRepository().apply { searchResult = { response(emptyList()) } }
        val state = JobHuntSearchViewModel(repository).uiState.value

        assertTrue(state is JobSearchUiState.Success)
        assertTrue((state as JobSearchUiState.Success).isEmpty)
    }

    @Test
    fun `a total the provider did not report stays null rather than defaulting to the page size`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { response(listOf(sampleResult()), total = null) }
        }
        val state = JobHuntSearchViewModel(repository).uiState.value as JobSearchUiState.Success
        assertNull(state.totalAvailable)
    }

    @Test
    fun `an unconfigured provider is Unavailable and offers no retry`() {
        // Retrying cannot configure a job provider, so offering a retry button would be a lie.
        val repository = FakeJobHuntRepository().apply {
            searchResult = { throw httpException(503) }
        }
        val state = JobHuntSearchViewModel(repository).uiState.value as JobSearchUiState.Failed

        assertTrue(state.failure is JobSearchFailure.Unavailable)
        assertFalse(state.failure.isRetryable)
    }

    @Test
    fun `a rate limit is retryable and says so`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { throw httpException(429) }
        }
        val state = JobHuntSearchViewModel(repository).uiState.value as JobSearchUiState.Failed

        assertTrue(state.failure is JobSearchFailure.Transient)
        assertTrue(state.failure.isRetryable)
        assertTrue(state.failure.message.contains("few minutes"))
    }

    @Test
    fun `a premium filter refusal is its own state, not a generic error`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { throw httpException(403) }
        }
        val state = JobHuntSearchViewModel(repository).uiState.value as JobSearchUiState.Failed

        assertTrue(state.failure is JobSearchFailure.PremiumRequired)
        assertTrue(state.failure.message.contains("Premium"))
    }

    @Test
    fun `an incomplete profile routes the student to setup rather than showing an error`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { throw httpException(422) }
        }
        val state = JobHuntSearchViewModel(repository).uiState.value as JobSearchUiState.Failed

        assertTrue(state.failure is JobSearchFailure.SetupRequired)
    }

    @Test
    fun `a connectivity failure is flagged as a network error`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { throw UnknownHostException("no route") }
        }
        val state = JobHuntSearchViewModel(repository).uiState.value as JobSearchUiState.Failed
        val failure = state.failure as JobSearchFailure.Transient

        assertTrue(failure.isNetworkError)
        assertTrue(failure.message.contains("connection", ignoreCase = true))
    }

    @Test
    fun `a timeout gets the gateway-timeout message, not the connectivity one`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { throw httpException(504) }
        }
        val state = JobHuntSearchViewModel(repository).uiState.value as JobSearchUiState.Failed
        assertTrue(state.failure.message.contains("too long"))
    }

    @Test
    fun `a client-side socket timeout is not misreported as no connection`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { throw SocketTimeoutException("slow") }
        }
        val state = JobHuntSearchViewModel(repository).uiState.value as JobSearchUiState.Failed
        val failure = state.failure as JobSearchFailure.Transient

        assertFalse(failure.isNetworkError)
        assertTrue(failure.message.contains("too long"))
    }

    @Test
    fun `the remote tab pushes work mode into the request`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { response(listOf(sampleResult())) }
        }
        val viewModel = JobHuntSearchViewModel(repository)
        viewModel.onTabSelected(JobFilterTab.Remote)

        assertEquals("remote", repository.lastSearchRequest?.workMode)
        assertNull(repository.lastSearchRequest?.employmentType)
    }

    @Test
    fun `the internship tab uses employment type, which the backend gates behind premium`() {
        // Sending it and surfacing the 403 is the honest outcome -- silently dropping the
        // filter would show unfiltered results under an "Internship" tab.
        val repository = FakeJobHuntRepository().apply {
            searchResult = { throw httpException(403) }
        }
        val viewModel = JobHuntSearchViewModel(repository)
        viewModel.onTabSelected(JobFilterTab.Internship)

        assertEquals("internship", repository.lastSearchRequest?.employmentType)
        val state = viewModel.uiState.value as JobSearchUiState.Failed
        assertTrue(state.failure is JobSearchFailure.PremiumRequired)
    }

    @Test
    fun `reselecting the active tab does not re-issue the search`() {
        var calls = 0
        val repository = FakeJobHuntRepository().apply {
            searchResult = {
                calls++
                response(listOf(sampleResult()))
            }
        }
        val viewModel = JobHuntSearchViewModel(repository)
        viewModel.onTabSelected(JobFilterTab.All)

        assertEquals(1, calls)
    }

    @Test
    fun `saving a job records it and marks the card saved`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { response(listOf(sampleResult())) }
            saveJobResult = { _, _ -> savedJob(id = 99) }
        }
        val viewModel = JobHuntSearchViewModel(repository)
        viewModel.toggleSave(1)

        val state = viewModel.uiState.value as JobSearchUiState.Success
        assertEquals(99, state.savedListingIds[1])
        assertEquals(listOf(1), repository.savedListingIds)
    }

    @Test
    fun `toggling an already-saved job removes it`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { response(listOf(sampleResult(savedJobId = 99))) }
        }
        val viewModel = JobHuntSearchViewModel(repository)
        viewModel.toggleSave(1)

        val state = viewModel.uiState.value as JobSearchUiState.Success
        assertFalse(state.savedListingIds.containsKey(1))
        assertEquals(listOf(99), repository.deletedSavedIds)
    }

    @Test
    fun `a failed save surfaces a message and leaves the results untouched`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { response(listOf(sampleResult())) }
            saveJobResult = { _, _ -> throw httpException(500) }
        }
        val viewModel = JobHuntSearchViewModel(repository)
        viewModel.toggleSave(1)

        val state = viewModel.uiState.value as JobSearchUiState.Success
        assertEquals(1, state.results.size)
        assertTrue(state.actionError != null)
        assertFalse(state.savedListingIds.containsKey(1))
    }

    @Test
    fun `the saving flag is cleared even when the save fails`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { response(listOf(sampleResult())) }
            saveJobResult = { _, _ -> throw httpException(500) }
        }
        val viewModel = JobHuntSearchViewModel(repository)
        viewModel.toggleSave(1)

        val state = viewModel.uiState.value as JobSearchUiState.Success
        assertTrue(state.savingListingIds.isEmpty())
    }

    @Test
    fun `already-saved results from the server are reflected without a second call`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { response(listOf(sampleResult(savedJobId = 42))) }
        }
        val state = JobHuntSearchViewModel(repository).uiState.value as JobSearchUiState.Success
        assertEquals(42, state.savedListingIds[1])
    }
}

class JobHuntSearchFilterTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(repository: FakeJobHuntRepository) = JobHuntSearchViewModel(repository)

    @Test
    fun `advanced filters reach the request`() {
        val repository = FakeJobHuntRepository().apply { searchResult = { response(emptyList()) } }
        viewModel(repository).applyFilters(
            JobFilters(location = "Pune", salaryMin = 600_000, maxDaysOld = 7)
        )

        val request = repository.lastSearchRequest!!
        assertEquals("Pune", request.location)
        assertEquals(600_000, request.salaryMin)
        assertEquals(7, request.maxDaysOld)
    }

    @Test
    fun `a premium filter is still sent for a free user so the server decides`() {
        // Client-side entitlement checks are a suggestion, not a control. Withholding the
        // request would also hide the upgrade prompt the server is trying to return.
        val repository = FakeJobHuntRepository().apply { searchResult = { response(emptyList()) } }
        viewModel(repository).applyFilters(JobFilters(experienceLevel = "fresher"))

        assertEquals("fresher", repository.lastSearchRequest!!.experienceLevel)
    }

    @Test
    fun `the quick tab still wins on the axis it owns`() {
        val repository = FakeJobHuntRepository().apply { searchResult = { response(emptyList()) } }
        val vm = viewModel(repository)
        vm.applyFilters(JobFilters(location = "Pune"))
        vm.onTabSelected(JobFilterTab.Remote)

        val request = repository.lastSearchRequest!!
        assertEquals("remote", request.workMode)
        // ...without discarding the rest of the sheet.
        assertEquals("Pune", request.location)
    }

    @Test
    fun `choosing a work mode in the sheet resets a conflicting tab`() {
        // Otherwise "Remote" stays highlighted while the sheet searched for on-site, and the
        // student cannot tell why the results look the way they do.
        val repository = FakeJobHuntRepository().apply { searchResult = { response(emptyList()) } }
        val vm = viewModel(repository)
        vm.onTabSelected(JobFilterTab.Remote)
        vm.applyFilters(JobFilters(workMode = "onsite"))

        assertEquals("onsite", repository.lastSearchRequest!!.workMode)
        val state = vm.uiState.value as JobSearchUiState.Success
        assertEquals(JobFilterTab.All, state.activeTab)
    }

    @Test
    fun `clearing removes every filter`() {
        val repository = FakeJobHuntRepository().apply { searchResult = { response(emptyList()) } }
        val vm = viewModel(repository)
        vm.applyFilters(JobFilters(location = "Pune", salaryMin = 600_000))
        vm.clearFilters()

        val request = repository.lastSearchRequest!!
        assertNull(request.location)
        assertNull(request.salaryMin)
        assertEquals(0, (vm.uiState.value as JobSearchUiState.Success).filters.activeCount)
    }

    @Test
    fun `applied filters survive into state so the sheet reopens on what is in effect`() {
        val repository = FakeJobHuntRepository().apply { searchResult = { response(emptyList()) } }
        val vm = viewModel(repository)
        vm.applyFilters(JobFilters(location = "Pune"))

        val state = vm.uiState.value as JobSearchUiState.Success
        assertEquals("Pune", state.filters.location)
    }
}

class JobHuntPaginationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun page(ids: List<Int>, total: Int? = null) = JobSearchResponseDto(
        results = ids.map { sampleResult(id = it) },
        totalAvailable = total,
        provider = "fake",
        attributionText = "Jobs by Fake",
        fetchedAt = "2026-09-08T10:00:00",
        scoreVersion = "v2",
    )

    @Test
    fun `a full first page with more available offers another`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { page((1..20).toList(), total = 45) }
        }
        val state = JobHuntSearchViewModel(repository).uiState.value as JobSearchUiState.Success
        assertTrue(state.canLoadMore)
    }

    @Test
    fun `a short page ends pagination even when no total is reported`() {
        // Several providers genuinely report no total. A short page is then the only signal
        // we have, and guessing "probably more" would show a button that returns nothing.
        val repository = FakeJobHuntRepository().apply {
            searchResult = { page(listOf(1, 2, 3), total = null) }
        }
        val state = JobHuntSearchViewModel(repository).uiState.value as JobSearchUiState.Success
        assertFalse(state.canLoadMore)
    }

    @Test
    fun `loading more appends rather than replacing`() {
        var call = 0
        val repository = FakeJobHuntRepository().apply {
            searchResult = {
                call++
                if (call == 1) page((1..20).toList(), total = 40) else page((21..40).toList(), total = 40)
            }
        }
        val vm = JobHuntSearchViewModel(repository)
        vm.loadMore()

        val state = vm.uiState.value as JobSearchUiState.Success
        assertEquals(40, state.results.size)
        assertEquals(2, state.page)
        assertFalse("everything is loaded, so stop offering more", state.canLoadMore)
    }

    @Test
    fun `a listing repeated across pages is not shown twice`() {
        // The same advert can legitimately land on two pages when the provider's own ordering
        // shifts between requests.
        var call = 0
        val repository = FakeJobHuntRepository().apply {
            searchResult = {
                call++
                if (call == 1) page((1..20).toList(), total = 100) else page((18..37).toList(), total = 100)
            }
        }
        val vm = JobHuntSearchViewModel(repository)
        vm.loadMore()

        val ids = (vm.uiState.value as JobSearchUiState.Success).results.map { it.listing.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `a failed page keeps the pages already on screen`() {
        var call = 0
        val repository = FakeJobHuntRepository().apply {
            searchResult = {
                call++
                if (call == 1) page((1..20).toList(), total = 100) else throw RuntimeException("boom")
            }
        }
        val vm = JobHuntSearchViewModel(repository)
        vm.loadMore()

        val state = vm.uiState.value as JobSearchUiState.Success
        assertEquals(20, state.results.size)
        assertTrue(state.actionError != null)
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun `pagination stops at the backend's own page cap`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { page((1..20).toList(), total = 10_000) }
        }
        val vm = JobHuntSearchViewModel(repository)
        repeat(12) { vm.loadMore() }

        val state = vm.uiState.value as JobSearchUiState.Success
        assertTrue("never past page 10", state.page <= 10)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun `filters are carried into every page, not just the first`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { page((1..20).toList(), total = 100) }
        }
        val vm = JobHuntSearchViewModel(repository)
        vm.applyFilters(JobFilters(location = "Pune"))
        vm.loadMore()

        assertEquals("Pune", repository.lastSearchRequest!!.location)
        assertEquals(2, repository.lastSearchRequest!!.page)
    }
}

class JobHuntDismissTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `dismissing removes the card and records the decision server-side`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { response(listOf(sampleResult(id = 1), sampleResult(id = 2))) }
            saveJobResult = { _, _ -> savedJob() }
        }
        val vm = JobHuntSearchViewModel(repository)
        vm.dismiss(1)

        val state = vm.uiState.value as JobSearchUiState.Success
        assertEquals(listOf(2), state.results.map { it.listing.id })
        assertEquals(listOf(1), repository.savedListingIds)
    }

    @Test
    fun `a failed dismiss leaves the card in place`() {
        // Hiding it locally after the server refused would make the job reappear on the next
        // search with no explanation.
        val repository = FakeJobHuntRepository().apply {
            searchResult = { response(listOf(sampleResult(id = 1))) }
            saveJobResult = { _, _ -> throw RuntimeException("boom") }
        }
        val vm = JobHuntSearchViewModel(repository)
        vm.dismiss(1)

        val state = vm.uiState.value as JobSearchUiState.Success
        assertEquals(listOf(1), state.results.map { it.listing.id })
        assertTrue(state.actionError != null)
    }
}

class SaveCurrentSearchTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `saving records the filters actually in effect, tab included`() {
        // The tab is part of what the student can see is applied. Saving the sheet alone
        // would reproduce a subtly wider search than the one on screen.
        val repository = FakeJobHuntRepository().apply {
            searchResult = { response(listOf(sampleResult())) }
            createSavedSearchResult = { name, filters ->
                SavedSearchDto(id = 1, name = name, filters = filters, createdAt = "2026-09-08T10:00:00")
            }
        }
        val vm = JobHuntSearchViewModel(repository)
        vm.applyFilters(JobFilters(location = "Pune"))
        vm.onTabSelected(JobFilterTab.Remote)
        vm.saveCurrentSearch("  Remote in Pune  ")

        val (name, filters) = repository.lastCreatedSavedSearch!!
        assertEquals("Remote in Pune", name)
        assertEquals("Pune", filters.location)
        assertEquals("remote", filters.workMode)
    }

    @Test
    fun `a blank name saves nothing`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { response(listOf(sampleResult())) }
        }
        val vm = JobHuntSearchViewModel(repository)
        vm.saveCurrentSearch("   ")

        assertNull(repository.lastCreatedSavedSearch)
    }
}
