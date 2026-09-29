package com.aiplacementos.app.ui.careerpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.RoleCatalogEntryDto
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.fakes.httpException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CareerPathRoleSelectionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun repository(
        roles: List<RoleCatalogEntryDto> = listOf(
            RoleCatalogEntryDto("data_analyst", "Data Analyst", "Data & AI", "Turns data into insights."),
        ),
    ) = FakeCareerPathRepository().apply {
        roleCatalogResult = { roles }
        roleCategoriesResult = { listOf("Data & AI", "Technology") }
    }

    @Test
    fun `load surfaces categories and roles`() {
        val viewModel = CareerPathRoleSelectionViewModel(repository())

        val state = viewModel.uiState.value
        assertTrue(state is CareerPathRoleSelectionUiState.Loaded)
        assertEquals(1, (state as CareerPathRoleSelectionUiState.Loaded).roles.size)
        assertTrue(state.categories.contains("Technology"))
    }

    @Test
    fun `selectCustomRole is never rejected as unsupported -- it always calls generate`() {
        val repo = repository().apply {
            generateResult = { _, customRole -> sampleCareerPath(displayRoleName = customRole ?: "Unknown") }
        }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Blockchain Developer")

        assertEquals("Blockchain Developer", repo.lastGenerateCustomRole)
        assertEquals(1, viewModel.generatedPathId.value)
    }

    @Test
    fun `rate limit failure surfaces a specific message and clears busy state`() {
        val repo = repository().apply {
            generateResult = { _, _ -> throw httpException(429) }
        }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Data Analyst")

        val state = viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded
        assertFalse(state.isGenerating)
        assertNotNull(state.generationError)
        assertTrue(state.generationError!!.contains("limit"))
    }

    @Test
    fun `network failure surfaces a generic message`() {
        val repo = repository().apply {
            // A connection that never opened: nothing reached the server, so this is a real
            // failure. An IOException after the request went out is not -- see
            // CareerPathGenerationLifecycleTest.
            generateResult = { _, _ -> throw java.net.ConnectException("down") }
        }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Data Analyst")

        val state = viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded
        assertFalse(state.isGenerating)
        assertNotNull(state.generationError)
    }

    @Test
    fun `a generic HTTP failure keeps its own message instead of borrowing the rate-limit one`() {
        val repo = repository().apply {
            generateResult = { _, _ -> throw httpException(500) }
        }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Data Analyst")

        val message = (viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded).generationError!!
        assertTrue("a server failure names the status so it can be reported", message.contains("500"))
        assertFalse("only a 429 may say the student hit a limit", message.contains("limit"))
    }

    @Test
    fun `a failed generation keeps the role list, so the next tap is the retry`() {
        val repo = repository().apply { generateResult = { _, _ -> throw httpException(500) } }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Data Analyst")

        // The failure is reported on the loaded screen rather than replacing it: with the
        // catalog gone there would be nothing left to tap, and no way back to trying again.
        val state = viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded
        assertEquals(1, state.roles.size)
        assertTrue(state.categories.isNotEmpty())
    }

    @Test
    fun `a successful generation leaves no error behind it`() {
        val repo = repository().apply {
            generateResult = { _, customRole -> sampleCareerPath(displayRoleName = customRole ?: "Unknown") }
        }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Data Analyst")

        assertNull((viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded).generationError)
    }

    @Test
    fun `retrying replaces the previous error rather than leaving a stale one on screen`() {
        var failWith: Int? = 429
        val repo = repository().apply {
            generateResult = { _, _ ->
                val code = failWith
                if (code != null) throw httpException(code)
                sampleCareerPath()
            }
        }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Data Analyst")
        val afterRateLimit = (viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded).generationError
        assertTrue(afterRateLimit!!.contains("limit"))

        failWith = 500
        viewModel.selectCustomRole("Data Analyst")
        val afterServerError = (viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded).generationError
        assertTrue("the second failure's own message is what shows", afterServerError!!.contains("500"))
        assertFalse(afterServerError.contains("limit"))

        failWith = null
        viewModel.selectCustomRole("Data Analyst")
        assertNull(
            "a success after a failure must not leave the old error on screen",
            (viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded).generationError,
        )
    }

    // ======================================================================================
    // Phase 10 -- one intended tap, one generation request.
    //
    // Until the catalog cards were wired, the screen's only tap target was the custom-role
    // button, which unmounts with the rest of the content the moment isGenerating flips. A
    // grid of tappable cards is a far easier surface to hit twice, so the guard is asserted
    // here rather than left to the screen's own unmounting.
    // ======================================================================================

    @Test
    fun `a second request while one is in flight does not start another`() {
        val inFlight = CompletableDeferred<Unit>()
        var calls = 0
        val repo = repository().apply {
            generateResult = { _, _ ->
                calls++
                inFlight.await()
                sampleCareerPath()
            }
        }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Data Analyst")
        // The first call is suspended inside the repository, so this arrives exactly as a
        // second tap on a card would while the spinner is up.
        viewModel.selectCustomRole("Software Engineer")

        assertEquals("one intention must not spend two of the hourly generations", 1, calls)
        assertEquals("and the role in flight is still the first one", "Data Analyst", repo.lastGenerateCustomRole)
        inFlight.complete(Unit)
    }

    @Test
    fun `the generating state is visible while the request is in flight`() {
        val inFlight = CompletableDeferred<Unit>()
        val repo = repository().apply {
            generateResult = { _, _ ->
                inFlight.await()
                sampleCareerPath()
            }
        }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Data Analyst")

        val state = viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded
        assertTrue("the screen swaps the catalog for the building state on this flag", state.isGenerating)
        assertNull(state.generationError)
        inFlight.complete(Unit)
    }

    @Test
    fun `the in-flight guard never blocks a retry after a failure`() {
        // The guard's whole risk is over-blocking. A failure puts isGenerating back to false,
        // so the next tap has to get through -- otherwise the screen becomes a dead end,
        // which is the defect Phase 9 closed.
        var calls = 0
        var failNext = true
        val repo = repository().apply {
            generateResult = { _, _ ->
                calls++
                if (failNext) throw httpException(500)
                sampleCareerPath()
            }
        }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Data Analyst")
        assertEquals(1, calls)
        assertNotNull((viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded).generationError)

        failNext = false
        viewModel.selectCustomRole("Data Analyst")

        assertEquals("the retry has to reach the repository", 2, calls)
        assertNull((viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded).generationError)
    }

    // ======================================================================================
    // Phase 11 -- a success must not leave the screen stuck on the building state.
    //
    // Reproduced on the emulator against a real backend: select a curated role, wait for the
    // generation to succeed, land on the detail screen, press Back once -- and the role
    // selection screen came back cycling "Analyzing your profile..." with no catalog, no
    // search and no custom-role field, permanently. NavGraph navigates without popUpTo, so
    // the screen's back-stack entry and this view model outlive the navigation, and nothing
    // ever retired isGenerating on the success path.
    // ======================================================================================

    @Test
    fun `a success leaves the screen usable once it is resumed`() {
        val repo = repository().apply {
            generateResult = { _, customRole -> sampleCareerPath(displayRoleName = customRole ?: "Unknown") }
        }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Data Analyst")
        // Still "generating" as the screen fades out -- clearing it here is what would flash
        // the catalog over the outgoing screen, so the success path deliberately leaves it.
        assertTrue((viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded).isGenerating)

        viewModel.onScreenResumed()

        val state = viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded
        assertFalse("Back must not return to a permanent building state", state.isGenerating)
        // Usable, not merely un-stuck: the catalog the student has to pick from is still there.
        assertEquals(1, state.roles.size)
        assertTrue(state.categories.isNotEmpty())
        assertNull(state.generationError)
    }

    @Test
    fun `another role can be generated after returning from a successful one`() {
        var calls = 0
        val repo = repository().apply {
            generateResult = { _, customRole ->
                calls++
                sampleCareerPath(displayRoleName = customRole ?: "Unknown")
            }
        }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Data Analyst")
        viewModel.onScreenResumed()
        viewModel.selectCustomRole("Software Engineer")

        assertEquals("the second selection has to reach the repository", 2, calls)
        assertEquals("Software Engineer", repo.lastGenerateCustomRole)
    }

    @Test
    fun `resuming while a generation is still running changes nothing`() {
        // The student backgrounded the app mid-generation and came back. The request is still
        // in flight, so the building state is still the truth -- and Phase 10's guard must
        // still refuse a second request. Clearing the flag here would break both.
        val inFlight = CompletableDeferred<Unit>()
        var calls = 0
        val repo = repository().apply {
            generateResult = { _, _ ->
                calls++
                inFlight.await()
                sampleCareerPath()
            }
        }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Data Analyst")
        viewModel.onScreenResumed()

        assertTrue(
            "a running generation must still show as running",
            (viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded).isGenerating,
        )
        viewModel.selectCustomRole("Software Engineer")
        assertEquals("and the duplicate-request guard must still hold", 1, calls)
        inFlight.complete(Unit)
    }

    @Test
    fun `resuming a screen that never generated anything is a no-op`() {
        val viewModel = CareerPathRoleSelectionViewModel(repository())

        val before = viewModel.uiState.value
        viewModel.onScreenResumed()

        // Fires on first entry too, so it has to leave an untouched screen alone.
        assertEquals(before, viewModel.uiState.value)
    }

    @Test
    fun `resuming after a failure leaves the error on screen`() {
        // Phase 9 semantics: a failure already clears isGenerating and shows its message.
        // Resuming must not quietly wipe that message before the student has read it.
        val repo = repository().apply { generateResult = { _, _ -> throw httpException(500) } }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Data Analyst")
        viewModel.onScreenResumed()

        val state = viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded
        assertFalse(state.isGenerating)
        assertNotNull("the Phase 9 failure message must survive a resume", state.generationError)
        assertTrue(state.generationError!!.contains("500"))
    }
}
