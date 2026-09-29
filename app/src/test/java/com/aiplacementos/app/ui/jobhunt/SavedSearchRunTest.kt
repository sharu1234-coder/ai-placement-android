package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.JobSearchRequestDto
import com.aiplacementos.app.data.remote.JobSearchResponseDto
import com.aiplacementos.app.data.remote.SavedSearchDto
import com.aiplacementos.app.fakes.FakeJobHuntRepository
import com.aiplacementos.app.fakes.httpException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun runResponse(count: Int, total: Int? = null) = JobSearchResponseDto(
    results = (1..count).map { sampleResult(id = it) },
    totalAvailable = total,
    provider = "fake",
    attributionText = "Jobs by Fake",
    fetchedAt = "2026-09-25T10:00:00",
    scoreVersion = "v1",
)

/**
 * Running a saved search.
 *
 * `POST /saved-searches/{id}/run` has existed since Job Hunt was built, and `runSavedSearch`
 * has existed on the repository, and nothing ever called either. The Run button rebuilt the
 * saved filters and replayed them through the ordinary search endpoint instead. The jobs that
 * came back were the same, so the gap was invisible on screen -- but the server only records
 * a run when its own endpoint performs it, so `last_run_at` and `last_result_count` stayed
 * NULL forever and the line under each saved search had nothing it could say.
 *
 * These pin the two halves: the run goes through the endpoint that records it, and it stops
 * being that run the moment the student searches for something else.
 */
class SavedSearchRunTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        savedSearchId: Int? = 7,
        filters: JobFilters = JobFilters(keywords = "data engineer"),
        onRun: suspend (Int) -> JobSearchResponseDto = { runResponse(3, total = 3) },
        onSearch: suspend (JobSearchRequestDto) -> JobSearchResponseDto = { runResponse(9) },
    ): Pair<JobHuntSearchViewModel, FakeJobHuntRepository> {
        val repository = FakeJobHuntRepository().apply {
            runSavedSearchResult = onRun
            searchResult = onSearch
        }
        return JobHuntSearchViewModel(repository, filters, savedSearchId) to repository
    }

    @Test
    fun `running a saved search goes through the endpoint that records it`() {
        val (viewModel, repository) = viewModel()

        assertEquals(listOf(7), repository.savedSearchRunIds)
        assertNull(
            "the filters must not be replayed through the ordinary search as well",
            repository.lastSearchRequest,
        )
        assertTrue(viewModel.uiState.value is JobSearchUiState.Success)
    }

    @Test
    fun `the jobs it returns are the ones shown`() {
        val (viewModel, _) = viewModel(onRun = { runResponse(3, total = 3) })

        val state = viewModel.uiState.value as JobSearchUiState.Success
        assertEquals(3, state.results.size)
        assertEquals(3, state.totalAvailable)
    }

    @Test
    fun `an ordinary visit still uses the ordinary search`() {
        val (_, repository) = viewModel(savedSearchId = null, filters = JobFilters())

        assertTrue(repository.savedSearchRunIds.isEmpty())
        assertNotNull(repository.lastSearchRequest)
    }

    @Test
    fun `a run that found nothing is a success, not a failure`() {
        // The distinction the whole feature turns on. "We looked and the market had nothing"
        // and "we could not look" are opposite claims about the same blank screen.
        val (viewModel, _) = viewModel(onRun = { runResponse(0, total = 0) })

        val state = viewModel.uiState.value
        assertTrue(state is JobSearchUiState.Success)
        assertTrue((state as JobSearchUiState.Success).isEmpty)
    }

    @Test
    fun `a failed run is a failure, not an empty result`() {
        val (viewModel, _) = viewModel(onRun = { throw httpException(502) })

        val state = viewModel.uiState.value
        assertTrue("a provider outage is never rendered as zero jobs", state is JobSearchUiState.Failed)
        assertTrue((state as JobSearchUiState.Failed).failure is JobSearchFailure.Transient)
    }

    @Test
    fun `an exhausted free allowance keeps its own state rather than reading as an error`() {
        // Running a saved search spends the same allowance as any search, so this is a real
        // response from this endpoint and not a hypothetical one.
        val (viewModel, _) = viewModel(onRun = { throw httpException(402) })

        val failure = (viewModel.uiState.value as JobSearchUiState.Failed).failure
        assertTrue(failure is JobSearchFailure.FreeLimitReached)
    }

    @Test
    fun `a premium-only saved search reports the upgrade rather than a breakage`() {
        val (viewModel, _) = viewModel(onRun = { throw httpException(403) })

        val failure = (viewModel.uiState.value as JobSearchUiState.Failed).failure
        assertTrue(failure is JobSearchFailure.PremiumRequired)
    }

    @Test
    fun `an unconfigured provider is reported as unavailable, not as no jobs`() {
        val (viewModel, _) = viewModel(onRun = { throw httpException(503) })

        val failure = (viewModel.uiState.value as JobSearchUiState.Failed).failure
        assertTrue(failure is JobSearchFailure.Unavailable)
    }

    @Test
    fun `being offline is reported as offline`() {
        val (viewModel, _) = viewModel(onRun = { throw IOException("no route") })

        val failure = (viewModel.uiState.value as JobSearchUiState.Failed).failure
        assertTrue(failure is JobSearchFailure.Transient)
        assertTrue((failure as JobSearchFailure.Transient).isNetworkError)
    }

    @Test
    fun `retrying a failed run goes back through the same endpoint`() {
        // And as the same operation: the id is not regenerated, so a retry after an outage
        // does not cost the student a second search.
        var attempts = 0
        val (viewModel, repository) = viewModel(
            onRun = {
                attempts++
                if (attempts == 1) throw httpException(502) else runResponse(2, total = 2)
            },
        )
        assertTrue(viewModel.uiState.value is JobSearchUiState.Failed)

        viewModel.retry()

        assertEquals(listOf(7, 7), repository.savedSearchRunIds)
        assertNull(repository.lastSearchRequest)
        assertEquals(1, repository.sentIdempotencyKeys.distinct().size)
        assertTrue(viewModel.uiState.value is JobSearchUiState.Success)
    }

    @Test
    fun `changing a filter stops this being a run of the saved search`() {
        // At that point the student is searching for something else that happened to start
        // from the saved one. Recording it as a run would put a result count against filters
        // the saved search was never run with.
        val (viewModel, repository) = viewModel()

        viewModel.applyFilters(JobFilters(keywords = "platform engineer"))

        assertEquals(listOf(7), repository.savedSearchRunIds)
        assertEquals("platform engineer", repository.lastSearchRequest?.keywords)
    }

    @Test
    fun `switching tab stops this being a run of the saved search`() {
        val (viewModel, repository) = viewModel()

        viewModel.onTabSelected(JobFilterTab.Remote)

        assertEquals(listOf(7), repository.savedSearchRunIds)
        assertEquals("remote", repository.lastSearchRequest?.workMode)
    }

    @Test
    fun `clearing filters stops this being a run of the saved search`() {
        val (viewModel, repository) = viewModel()

        viewModel.clearFilters()

        assertEquals(listOf(7), repository.savedSearchRunIds)
        assertNotNull(repository.lastSearchRequest)
    }

    @Test
    fun `the saved filters still seed the sheet and the next page`() {
        // The id alone would leave the filter sheet empty and page two unfiltered, because the
        // run endpoint takes neither. Both still travel.
        val (viewModel, repository) = viewModel(
            filters = JobFilters(keywords = "data engineer", location = "Hyderabad"),
            onRun = { runResponse(20, total = 45) },
        )
        val state = viewModel.uiState.value as JobSearchUiState.Success
        assertEquals("data engineer", state.filters.keywords)
        assertEquals("Hyderabad", state.filters.location)

        viewModel.loadMore()

        assertEquals("data engineer", repository.lastSearchRequest?.keywords)
        assertEquals("Hyderabad", repository.lastSearchRequest?.location)
        assertEquals(2, repository.lastSearchRequest?.page)
    }
}

/**
 * The line under a saved search, and the four different things it has to be able to say.
 *
 * Every one of them is read from a field the server writes. None is derived from what this
 * client did, because a local copy would start disagreeing with the server the first time a
 * run failed or a second device ran the same search.
 */
class SavedSearchRunStateLabelTest {
    private fun search(
        lastRunAt: String? = null,
        lastSuccessAt: String? = null,
        lastErrorAt: String? = null,
        lastResultCount: Int? = null,
    ) = SavedSearchDto(
        id = 1,
        name = "Dubai data roles",
        lastRunAt = lastRunAt,
        lastSuccessAt = lastSuccessAt,
        lastErrorAt = lastErrorAt,
        lastResultCount = lastResultCount,
        createdAt = "2026-09-01T10:00:00",
    )

    @Test
    fun `a search that has never run says so rather than showing a count`() {
        assertEquals("Not run yet", savedSearchRunStateLabel(search()))
    }

    @Test
    fun `a successful run reports its real count`() {
        assertEquals(
            "4 jobs last time",
            savedSearchRunStateLabel(
                search(
                    lastRunAt = "2026-09-25T10:00:00",
                    lastSuccessAt = "2026-09-25T10:00:00",
                    lastResultCount = 4,
                ),
            ),
        )
    }

    @Test
    fun `a run that found nothing says nothing was found, not that it never ran`() {
        assertEquals(
            "No jobs last time",
            savedSearchRunStateLabel(
                search(
                    lastRunAt = "2026-09-25T10:00:00",
                    lastSuccessAt = "2026-09-25T10:00:00",
                    lastResultCount = 0,
                ),
            ),
        )
    }

    @Test
    fun `one job is not pluralised`() {
        assertEquals(
            "1 job last time",
            savedSearchRunStateLabel(
                search(
                    lastRunAt = "2026-09-25T10:00:00",
                    lastSuccessAt = "2026-09-25T10:00:00",
                    lastResultCount = 1,
                ),
            ),
        )
    }

    @Test
    fun `a failed run says so and keeps the last count it actually knows`() {
        // The server deliberately does not overwrite last_result_count on a failure, because
        // replacing a real count with a zero would tell the student their search stopped
        // matching anything when what happened is that we could not ask. The label has to
        // carry that distinction through rather than flattening it.
        assertEquals(
            "Last run didn't finish · 4 jobs last time",
            savedSearchRunStateLabel(
                search(
                    lastRunAt = "2026-09-25T12:00:00",
                    lastSuccessAt = "2026-09-25T10:00:00",
                    lastErrorAt = "2026-09-25T12:00:00",
                    lastResultCount = 4,
                ),
            ),
        )
    }

    @Test
    fun `a search whose only run failed reports no count at all`() {
        assertEquals(
            "Last run didn't finish",
            savedSearchRunStateLabel(
                search(lastRunAt = "2026-09-25T12:00:00", lastErrorAt = "2026-09-25T12:00:00"),
            ),
        )
    }

    @Test
    fun `a success after an earlier failure reports the success`() {
        assertEquals(
            "6 jobs last time",
            savedSearchRunStateLabel(
                search(
                    lastRunAt = "2026-09-25T14:00:00",
                    lastSuccessAt = "2026-09-25T14:00:00",
                    lastErrorAt = "2026-09-25T12:00:00",
                    lastResultCount = 6,
                ),
            ),
        )
    }

    @Test
    fun `sub-second precision does not flip the ordering`() {
        // The two timestamps come from one clock but not always with the same number of
        // fractional digits, and the comparison has to stay correct across that.
        assertEquals(
            "2 jobs last time",
            savedSearchRunStateLabel(
                search(
                    lastRunAt = "2026-09-25T12:00:01",
                    lastSuccessAt = "2026-09-25T12:00:01",
                    lastErrorAt = "2026-09-25T12:00:00.999999",
                    lastResultCount = 2,
                ),
            ),
        )
    }
}
