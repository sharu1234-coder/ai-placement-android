package com.aiplacementos.app.ui.careerpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CareerPathDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Idempotency-Key semantics (Phase 24-C) ---

    @Test
    fun `a failed refresh keeps its key, and a refresh that worked gets a new one`() {
        val repository = FakeCareerPathRepository().apply {
            pathResult = { sampleCareerPath() }
            refreshResult = { throw java.net.UnknownHostException("reply lost") }
        }
        val viewModel = CareerPathDetailViewModel(1, repository)

        viewModel.refresh()
        viewModel.refresh()
        assertEquals("a retry of the same refresh", 1, repository.keys.distinct().size)

        repository.refreshResult = { sampleCareerPath() }
        viewModel.refresh()
        val afterSuccess = repository.keys.last()
        viewModel.refresh()

        assertEquals("the key survived until it succeeded", repository.keys[0], afterSuccess)
        assertNotEquals("and the next refresh is a new operation", afterSuccess, repository.keys.last())
    }

    @Test
    fun `load surfaces the path`() {
        val repository = FakeCareerPathRepository().apply { pathResult = { sampleCareerPath() } }
        val viewModel = CareerPathDetailViewModel(1, repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<*>)
        assertEquals("Data Analyst", (state as UiState.Success).data.displayRoleName)
    }

    @Test
    fun `load surfaces an error message on failure`() {
        val repository = FakeCareerPathRepository().apply { pathResult = { throw java.io.IOException("down") } }
        val viewModel = CareerPathDetailViewModel(1, repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }

    @Test
    fun `marking a skill item complete reloads the path`() {
        var reloadCount = 0
        val repository = FakeCareerPathRepository().apply {
            pathResult = { reloadCount++; sampleCareerPath() }
            markSkillItemResult = { _, _ -> sampleSkillItem(status = "completed") }
        }
        val viewModel = CareerPathDetailViewModel(1, repository)
        val loadsBeforeComplete = reloadCount

        viewModel.markSkillItemComplete(1)

        assertEquals(1, repository.lastMarkedSkillItemId)
        assertTrue(reloadCount > loadsBeforeComplete)
    }

    @Test
    fun `refresh replaces the current state with the regenerated path`() {
        val repository = FakeCareerPathRepository().apply {
            pathResult = { sampleCareerPath() }
            refreshResult = { sampleCareerPath(displayRoleName = "Data Analyst (refreshed)") }
        }
        val viewModel = CareerPathDetailViewModel(1, repository)

        viewModel.refresh()

        val state = viewModel.uiState.value as UiState.Success
        assertEquals("Data Analyst (refreshed)", state.data.displayRoleName)
    }

    @Test
    fun `CareerPathDetailViewModel has no dependency on PlacementPathRepository -- structurally cannot touch Roadmap state`() {
        // A behavioral mock-based test could only ever show "didn't happen to call it in this
        // scenario" -- this asserts the stronger, structural guarantee: the class doesn't even
        // have a reference to PlacementPathRepository to call, the same enforcement style used
        // elsewhere in this codebase (e.g. the ATS scoring engine's target_role omission).
        val constructorParamTypeNames = CareerPathDetailViewModel::class.java.declaredConstructors
            .flatMap { it.parameterTypes.toList() }
            .map { it.name }
        assertFalse(constructorParamTypeNames.any { it.contains("PlacementPath") })
    }
}
