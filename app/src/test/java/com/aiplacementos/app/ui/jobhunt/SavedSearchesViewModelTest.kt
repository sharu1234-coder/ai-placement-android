package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.JobSearchRequestDto
import com.aiplacementos.app.data.remote.SavedSearchDto
import com.aiplacementos.app.fakes.FakeJobHuntRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun savedSearch(
    id: Int = 1,
    name: String = "Remote Python",
    filters: JobSearchRequestDto = JobSearchRequestDto(keywords = "python", workMode = "remote"),
    lastRunAt: String? = null,
    lastResultCount: Int? = null,
) = SavedSearchDto(
    id = id,
    name = name,
    filters = filters,
    lastRunAt = lastRunAt,
    lastResultCount = lastResultCount,
    createdAt = "2026-09-08T10:00:00",
)

class SavedSearchesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `lists saved searches`() {
        val repository = FakeJobHuntRepository().apply {
            listSavedSearchesResult = { listOf(savedSearch(), savedSearch(id = 2, name = "Interns")) }
        }
        val state = SavedSearchesViewModel(repository).uiState.value

        assertTrue(state is SavedSearchesUiState.Success)
        assertEquals(2, (state as SavedSearchesUiState.Success).searches.size)
    }

    @Test
    fun `an empty list is a real success, not an error`() {
        val repository = FakeJobHuntRepository().apply { listSavedSearchesResult = { emptyList() } }
        val state = SavedSearchesViewModel(repository).uiState.value

        assertTrue(state is SavedSearchesUiState.Success)
        assertTrue((state as SavedSearchesUiState.Success).isEmpty)
    }

    @Test
    fun `deleting removes the row`() {
        val repository = FakeJobHuntRepository().apply {
            listSavedSearchesResult = { listOf(savedSearch(id = 1), savedSearch(id = 2)) }
            deleteSavedSearchResult = { }
        }
        val vm = SavedSearchesViewModel(repository)
        vm.delete(1)

        val state = vm.uiState.value as SavedSearchesUiState.Success
        assertEquals(listOf(2), state.searches.map { it.id })
    }

    @Test
    fun `a failed delete keeps the row and reports why`() {
        // Removing it locally after the server refused would make it reappear on the next
        // load with no explanation.
        val repository = FakeJobHuntRepository().apply {
            listSavedSearchesResult = { listOf(savedSearch(id = 1)) }
            deleteSavedSearchResult = { throw RuntimeException("boom") }
        }
        val vm = SavedSearchesViewModel(repository)
        vm.delete(1)

        val state = vm.uiState.value as SavedSearchesUiState.Success
        assertEquals(listOf(1), state.searches.map { it.id })
        assertTrue(state.actionError != null)
        assertTrue(state.deletingIds.isEmpty())
    }
}

class SavedSearchSummaryTest {
    @Test
    fun `only filters that are set appear`() {
        val summary = savedSearchSummary(
            savedSearch(filters = JobSearchRequestDto(keywords = "python", location = "Pune"))
        )
        assertEquals("python · Pune", summary)
    }

    @Test
    fun `an unset filter contributes nothing rather than the word Anywhere`() {
        // The saved search did not say "anywhere". It said nothing, and printing a value the
        // student never chose is how a saved search stops meaning what they saved.
        val summary = savedSearchSummary(savedSearch(filters = JobSearchRequestDto(keywords = "sql")))
        assertEquals("sql", summary)
    }

    @Test
    fun `a search with no filters at all says so plainly`() {
        val summary = savedSearchSummary(savedSearch(filters = JobSearchRequestDto()))
        assertTrue(summary.contains("No filters"))
    }

    @Test
    fun `premium filters are described in the same words the sheet used`() {
        val summary = savedSearchSummary(
            savedSearch(
                filters = JobSearchRequestDto(
                    employmentType = "internship",
                    experienceLevel = "fresher",
                    maxDaysOld = 7,
                    salaryMin = 600_000,
                )
            )
        )
        assertTrue(summary.contains("Internship"))
        assertTrue(summary.contains("0-1 years"))
        assertTrue(summary.contains("Posted this week"))
        assertTrue(summary.contains("600000"))
    }
}

class SavedSearchLastRunTest {
    @Test
    fun `a search that never ran shows nothing, not zero results`() {
        // "0 jobs last time" would tell a student the market is empty when we never looked.
        assertNull(savedSearchLastRunLabel(savedSearch(lastRunAt = null, lastResultCount = null)))
    }

    @Test
    fun `a run with a count reports the count`() {
        assertEquals(
            "4 jobs last time",
            savedSearchLastRunLabel(savedSearch(lastRunAt = "2026-09-08T10:00:00", lastResultCount = 4)),
        )
    }

    @Test
    fun `one job is not pluralised`() {
        assertEquals(
            "1 job last time",
            savedSearchLastRunLabel(savedSearch(lastRunAt = "2026-09-08T10:00:00", lastResultCount = 1)),
        )
    }

    @Test
    fun `a run with no recorded count does not invent one`() {
        assertEquals(
            "Run once already",
            savedSearchLastRunLabel(savedSearch(lastRunAt = "2026-09-08T10:00:00", lastResultCount = null)),
        )
    }
}

class FiltersRoundTripTest {
    @Test
    fun `a saved search replays into the same filters it was saved from`() {
        val original = JobFilters(
            keywords = "data engineer",
            location = "Hyderabad",
            workMode = "hybrid",
            employmentType = "full_time",
            experienceLevel = "junior",
            salaryMin = 800_000,
            maxDaysOld = 30,
        )
        assertEquals(original, original.toRequest().toFilters())
    }

    @Test
    fun `page and page size are dropped on the way back`() {
        // They describe one fetch, not what the student was looking for. Restoring "page 4"
        // would open on results whose first three pages they never saw.
        val restored = JobSearchRequestDto(keywords = "sql", page = 4, pageSize = 50).toFilters()
        assertEquals("sql", restored.keywords)
        assertEquals(1, restored.toRequest().page)
    }
}
