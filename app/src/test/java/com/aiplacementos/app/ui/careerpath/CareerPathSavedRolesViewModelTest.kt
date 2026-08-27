package com.aiplacementos.app.ui.careerpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CareerPathSavedRolesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load surfaces saved roles`() {
        val repository = FakeCareerPathRepository().apply {
            allResult = { listOf(sampleSummary(id = 1), sampleSummary(id = 2, isPrimary = false)) }
        }
        val viewModel = CareerPathSavedRolesViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<*>)
        assertEquals(2, (state as UiState.Success).data.size)
    }

    @Test
    fun `load surfaces Empty when nothing has been saved yet`() {
        val repository = FakeCareerPathRepository().apply { allResult = { emptyList() } }
        val viewModel = CareerPathSavedRolesViewModel(repository)

        assertTrue(viewModel.uiState.value is UiState.Empty)
    }

    @Test
    fun `setCurrent reloads the list after switching`() {
        var loadCount = 0
        val repository = FakeCareerPathRepository().apply {
            allResult = { loadCount++; listOf(sampleSummary(id = 2, isPrimary = true)) }
            setCurrentResult = { _ -> sampleCareerPath(id = 2) }
        }
        val viewModel = CareerPathSavedRolesViewModel(repository)
        val loadsBeforeSwitch = loadCount

        viewModel.setCurrent(2)

        assertEquals(2, repository.lastSetCurrentPathId)
        assertTrue(loadCount > loadsBeforeSwitch)
    }
}
