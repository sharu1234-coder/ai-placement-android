package com.aiplacementos.app.ui.jobhunt

import androidx.lifecycle.SavedStateHandle
import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.JobHuntEntitlementDto
import com.aiplacementos.app.data.remote.JobSearchProfileDto
import com.aiplacementos.app.data.remote.JobSearchResponseDto
import com.aiplacementos.app.fakes.FakeJobHuntRepository
import com.aiplacementos.app.fakes.httpException
import com.aiplacementos.app.fakes.httpExceptionWithBody
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The client side of "one free Job Hunt search".
 *
 * Nothing here enforces anything, and that is the first thing to be clear about: the server
 * decides, on every search request, and these tests exist for the two jobs the client actually
 * has. First, saying the right thing -- a refusal that reads as a product boundary rather than
 * a bug, and an offer that appears before the tap rather than a refusal after it. Second, not
 * *wasting* the student's one search: an operation id that survives the Android lifecycle so a
 * killed process or a re-sent request is recognised as the same search rather than a second one.
 *
 * The second job is the one with teeth. A free student who loses their connection at the wrong
 * moment, or whose process is killed while the search is in flight, must not come back to "your
 * free search is used" having never seen a result.
 */
class JobHuntFreeUseTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val limitBody = """
        {"detail":{"code":"JOB_HUNT_FREE_LIMIT_REACHED",
        "message":"Your free Job Hunt search has already been used.","requires_premium":true}}
    """.trimIndent()

    /** An empty but successful search. fetchedAt is required on the wire type, so a fixed
     * instant is used rather than "now" -- a test that varies with the clock is a test that
     * eventually fails for reasons unrelated to the code. */
    private fun emptyResponse() = JobSearchResponseDto(
        results = emptyList(),
        fetchedAt = "2026-09-10T10:00:00",
    )

    private fun repositoryReturning(
        response: JobSearchResponseDto = emptyResponse(),
    ) = FakeJobHuntRepository().apply { searchResult = { response } }

    private fun viewModel(
        repository: FakeJobHuntRepository,
        handle: SavedStateHandle? = null,
    ) = JobHuntSearchViewModel(repository, JobFilters(), savedStateHandle = handle)

    // -- Classification -------------------------------------------------------------------

    @Test
    fun `a 402 is the free-limit state, not a generic error`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { throw httpExceptionWithBody(402, limitBody) }
        }

        val state = viewModel(repository).uiState.value as JobSearchUiState.Failed

        assertTrue(state.failure is JobSearchFailure.FreeLimitReached)
    }

    @Test
    fun `the free-limit state offers no retry`() {
        // A retry would be refused identically. Offering one makes a deliberate product
        // boundary look like a transient fault, and invites the student to tap it repeatedly.
        val failure = JobSearchFailure.FreeLimitReached("used")

        assertFalse(failure.isRetryable)
    }

    @Test
    fun `the server's own wording is what gets shown`() {
        // The server knows the plan and owns the copy. Composing the sentence here as well is
        // how the two end up disagreeing after one of them is reworded.
        val repository = FakeJobHuntRepository().apply {
            searchResult = { throw httpExceptionWithBody(402, limitBody) }
        }

        val state = viewModel(repository).uiState.value as JobSearchUiState.Failed

        assertEquals("Your free Job Hunt search has already been used.", state.failure.message)
    }

    @Test
    fun `an unparseable 402 body still produces a usable message`() {
        // An error handler that throws is worse than the error it was handling, and "you have
        // used your free search" is true whatever the body looked like.
        val repository = FakeJobHuntRepository().apply {
            searchResult = { throw httpExceptionWithBody(402, "not json at all") }
        }

        val state = viewModel(repository).uiState.value as JobSearchUiState.Failed

        assertTrue(state.failure is JobSearchFailure.FreeLimitReached)
        assertTrue(state.failure.message.isNotBlank())
    }

    @Test
    fun `a 402 carrying some other code falls back rather than borrowing this screen's copy`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = {
                throw httpExceptionWithBody(402, """{"detail":{"code":"SOMETHING_ELSE","message":"nope"}}""")
            }
        }

        val state = viewModel(repository).uiState.value as JobSearchUiState.Failed

        assertTrue(state.failure is JobSearchFailure.FreeLimitReached)
        assertNotEquals("nope", state.failure.message)
    }

    @Test
    fun `the free limit and the premium-filter refusal are different states`() {
        // Both mean "Premium", and they lead to different screens: one says the search is free
        // without that filter, the other says searching again is the thing that is not free.
        val limit = FakeJobHuntRepository().apply {
            searchResult = { throw httpExceptionWithBody(402, limitBody) }
        }
        val filter = FakeJobHuntRepository().apply {
            searchResult = { throw httpException(403) }
        }

        val limitFailure = (viewModel(limit).uiState.value as JobSearchUiState.Failed).failure
        val filterFailure = (viewModel(filter).uiState.value as JobSearchUiState.Failed).failure

        assertTrue(limitFailure is JobSearchFailure.FreeLimitReached)
        assertTrue(filterFailure is JobSearchFailure.PremiumRequired)
    }

    @Test
    fun `a 409 is transient and retryable, not a limit`() {
        // Only reachable from a replayed or mismatched request id. It is a client-side stumble,
        // and the recovery is a fresh search rather than an upgrade prompt.
        val repository = FakeJobHuntRepository().apply {
            searchResult = { throw httpException(409) }
        }

        val failure = (viewModel(repository).uiState.value as JobSearchUiState.Failed).failure

        assertTrue(failure is JobSearchFailure.Transient)
        assertTrue(failure.isRetryable)
    }

    @Test
    fun `the other failure kinds are unchanged by any of this`() {
        // Guard against the new branch swallowing an existing one: the taxonomy the screen
        // already depends on has to keep resolving exactly as it did.
        val cases = mapOf(
            503 to JobSearchFailure.Unavailable::class,
            429 to JobSearchFailure.Transient::class,
            504 to JobSearchFailure.Transient::class,
            502 to JobSearchFailure.Transient::class,
            422 to JobSearchFailure.SetupRequired::class,
        )

        cases.forEach { (code, expected) ->
            val repository = FakeJobHuntRepository().apply {
                searchResult = { throw httpException(code) }
            }
            val failure = (viewModel(repository).uiState.value as JobSearchUiState.Failed).failure
            assertEquals("HTTP $code", expected, failure::class)
        }
    }

    @Test
    fun `a connectivity failure is still a network error and not a limit`() {
        val repository = FakeJobHuntRepository().apply {
            searchResult = { throw UnknownHostException("no route") }
        }

        val failure = (viewModel(repository).uiState.value as JobSearchUiState.Failed).failure

        assertTrue(failure is JobSearchFailure.Transient)
        assertTrue((failure as JobSearchFailure.Transient).isNetworkError)
    }

    // -- One tap is one operation ---------------------------------------------------------

    @Test
    fun `the first search sends an idempotency key`() {
        val repository = repositoryReturning()

        viewModel(repository)

        assertEquals(1, repository.sentIdempotencyKeys.size)
        assertTrue(repository.sentIdempotencyKeys.single()?.isNotBlank() == true)
    }

    @Test
    fun `a retry after a failure reuses the same key`() {
        // The scenario: the provider was down, the student taps Retry. That is the same search
        // they already asked for, and the server treats a failed operation as still owed -- so
        // reusing the key is what makes the retry free. A new key would make it a new search.
        var shouldFail = true
        val repository = FakeJobHuntRepository().apply {
            searchResult = {
                if (shouldFail) throw httpException(502)
                emptyResponse()
            }
        }
        val model = viewModel(repository)

        shouldFail = false
        model.retry()

        assertEquals(2, repository.sentIdempotencyKeys.size)
        assertEquals(repository.sentIdempotencyKeys[0], repository.sentIdempotencyKeys[1])
    }

    @Test
    fun `several retries all stay within the one operation`() {
        // Repeated UI events -- an impatient student tapping Retry, or a button with no debounce
        // -- must not become several searches. The key is what makes that true on the server.
        val repository = FakeJobHuntRepository().apply {
            searchResult = { throw httpException(502) }
        }
        val model = viewModel(repository)

        repeat(4) { model.retry() }

        assertEquals(5, repository.sentIdempotencyKeys.size)
        assertEquals(1, repository.sentIdempotencyKeys.toSet().size)
    }

    @Test
    fun `loading another page stays within the same operation`() {
        // A page is part of the search, not a search. Charging per page would make "load more"
        // the most expensive button in the app.
        val repository = FakeJobHuntRepository().apply {
            searchResult = {
                JobSearchResponseDto(
                    results = List(20) { index -> sampleResult(id = index + 1) },
                    totalAvailable = 100,
                    fetchedAt = "2026-09-10T10:00:00",
                )
            }
        }
        val model = viewModel(repository)

        model.loadMore()

        assertEquals(2, repository.sentIdempotencyKeys.size)
        assertEquals(repository.sentIdempotencyKeys[0], repository.sentIdempotencyKeys[1])
    }

    @Test
    fun `changing the tab starts a new operation`() {
        // A different tab is a different search. Reusing the key would have the server refuse
        // it as a conflicting replay rather than run it.
        val repository = repositoryReturning()
        val model = viewModel(repository)

        model.onTabSelected(JobFilterTab.Remote)

        assertEquals(2, repository.sentIdempotencyKeys.size)
        assertNotEquals(repository.sentIdempotencyKeys[0], repository.sentIdempotencyKeys[1])
    }

    @Test
    fun `applying filters starts a new operation`() {
        val repository = repositoryReturning()
        val model = viewModel(repository)

        model.applyFilters(JobFilters(location = "Pune"))

        assertNotEquals(repository.sentIdempotencyKeys[0], repository.sentIdempotencyKeys[1])
    }

    @Test
    fun `clearing filters starts a new operation`() {
        val repository = repositoryReturning()
        val model = viewModel(repository)
        model.applyFilters(JobFilters(location = "Pune"))

        model.clearFilters()

        assertEquals(3, repository.sentIdempotencyKeys.size)
        assertEquals(3, repository.sentIdempotencyKeys.toSet().size)
    }

    @Test
    fun `selecting the tab that is already active does nothing at all`() {
        val repository = repositoryReturning()
        val model = viewModel(repository)

        model.onTabSelected(JobFilterTab.All)

        assertEquals(1, repository.sentIdempotencyKeys.size)
    }

    // -- Surviving the Android lifecycle ---------------------------------------------------

    @Test
    fun `a configuration change does not start a new operation`() {
        // The ViewModel survives a rotation, so the id does too -- there is nothing to restore
        // and nothing to re-send. Pinned because a future refactor that moved the id into the
        // composable would break this silently.
        val repository = repositoryReturning()
        val handle = SavedStateHandle()
        val model = viewModel(repository, handle)

        model.retry()

        assertEquals(1, repository.sentIdempotencyKeys.toSet().size)
    }

    @Test
    fun `process death and recreation replays the same operation, not a second one`() {
        // The case this whole mechanism exists for. Android kills the app mid-search; the
        // student returns; the screen re-runs the search. Without a surviving id the server
        // would read that as a second search, and a free student would lose theirs to a
        // lifecycle event they never saw.
        val repository = repositoryReturning()
        val handle = SavedStateHandle()
        viewModel(repository, handle)
        val keyBeforeDeath = repository.sentIdempotencyKeys.single()

        // A new ViewModel over the restored SavedStateHandle is exactly what the framework
        // constructs on the way back.
        viewModel(repository, handle)

        assertEquals(2, repository.sentIdempotencyKeys.size)
        assertEquals(keyBeforeDeath, repository.sentIdempotencyKeys[1])
    }

    @Test
    fun `a genuinely new screen visit is a new operation`() {
        // The other half of the previous test: restoring state must not turn every future visit
        // into a replay of one search from last week.
        val repository = repositoryReturning()
        viewModel(repository, SavedStateHandle())

        viewModel(repository, SavedStateHandle())

        assertNotEquals(repository.sentIdempotencyKeys[0], repository.sentIdempotencyKeys[1])
    }

    @Test
    fun `the view model works without a saved state handle`() {
        // Tests and previews construct it this way, and the server is authoritative either
        // way -- a missing handle costs process-death deduplication, not correctness.
        val repository = repositoryReturning()

        val state = viewModel(repository, handle = null).uiState.value

        assertTrue(state is JobSearchUiState.Success)
        assertTrue(repository.sentIdempotencyKeys.single()?.isNotBlank() == true)
    }

    @Test
    fun `the key fits the shape the server accepts`() {
        // The server rejects anything outside 8-64 characters of letters, digits, dot,
        // underscore, colon or hyphen -- with a 400, which would surface as an unexplained
        // failure on the first search of every session if the client ever drifted.
        val repository = repositoryReturning()

        viewModel(repository)

        val key = repository.sentIdempotencyKeys.single()!!
        assertTrue("length ${key.length}", key.length in 8..64)
        assertTrue(key, key.all { it.isLetterOrDigit() || it in ".-_:" })
    }

    // -- What the home screen says before the tap ------------------------------------------

    @Test
    fun `a student with their free search intact is told so`() {
        val repository = FakeJobHuntRepository().apply {
            profileResult = { JobSearchProfileDto(isReadyToSearch = true) }
            entitlementResult = {
                JobHuntEntitlementDto(
                    tier = "free",
                    freeUsesRemaining = 1,
                    hasUsedFreeSearch = false,
                    requiresPremium = false,
                )
            }
        }

        val state = JobHuntHomeViewModel(repository).uiState.value as JobHuntHomeUiState.Success

        assertEquals(false, state.entitlement?.requiresPremium)
        assertEquals(1, state.entitlement?.freeUsesRemaining)
    }

    @Test
    fun `a student who has used it is told before they tap, not after`() {
        val repository = FakeJobHuntRepository().apply {
            profileResult = { JobSearchProfileDto(isReadyToSearch = true) }
            entitlementResult = {
                JobHuntEntitlementDto(
                    tier = "free",
                    freeUsesRemaining = 0,
                    hasUsedFreeSearch = true,
                    requiresPremium = true,
                )
            }
        }

        val state = JobHuntHomeViewModel(repository).uiState.value as JobHuntHomeUiState.Success

        assertEquals(true, state.entitlement?.requiresPremium)
    }

    @Test
    fun `a subscriber is never told they need premium`() {
        // hasUsedFreeSearch stays true for a subscriber who used it before paying. The banner
        // keys off requiresPremium, not off history, or every paying student would see an
        // upgrade prompt for something they already bought.
        val repository = FakeJobHuntRepository().apply {
            profileResult = { JobSearchProfileDto(isReadyToSearch = true) }
            entitlementResult = {
                JobHuntEntitlementDto(
                    tier = "premium",
                    unlimited = true,
                    hasUsedFreeSearch = true,
                    requiresPremium = false,
                )
            }
        }

        val state = JobHuntHomeViewModel(repository).uiState.value as JobHuntHomeUiState.Success

        assertEquals(true, state.entitlement?.unlimited)
        assertEquals(false, state.entitlement?.requiresPremium)
    }

    @Test
    fun `a failed entitlement call does not break the screen and makes no claim`() {
        // Null, rendered as nothing at all. Guessing "you still have one" sets up a refusal the
        // student was told would not come; guessing the opposite hides a search they own.
        val repository = FakeJobHuntRepository().apply {
            profileResult = { JobSearchProfileDto(isReadyToSearch = true) }
            entitlementResult = { throw httpException(500) }
        }

        val state = JobHuntHomeViewModel(repository).uiState.value

        assertTrue(state is JobHuntHomeUiState.Success)
        assertEquals(null, (state as JobHuntHomeUiState.Success).entitlement)
    }

    @Test
    fun `the profile still loads when entitlement is unavailable`() {
        // Degrading independently, the same rule the summary tiles already follow: a student
        // must never lose their way into setup or search because a secondary call failed.
        val repository = FakeJobHuntRepository().apply {
            profileResult = { JobSearchProfileDto(targetRoleTitle = "Data Analyst", isReadyToSearch = true) }
            entitlementResult = { throw UnknownHostException("offline") }
        }

        val state = JobHuntHomeViewModel(repository).uiState.value as JobHuntHomeUiState.Success

        assertEquals("Data Analyst", state.profile.targetRoleTitle)
    }
}
