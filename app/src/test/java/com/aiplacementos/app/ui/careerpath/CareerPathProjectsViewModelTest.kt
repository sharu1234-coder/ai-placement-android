package com.aiplacementos.app.ui.careerpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CareerPathProjectsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load surfaces projects`() {
        val repository = FakeCareerPathRepository().apply {
            projectsResult = { listOf(sampleProject(), sampleProject(id = 2, difficulty = "advanced")) }
        }
        val viewModel = CareerPathProjectsViewModel(1, repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<*>)
        assertEquals(2, (state as UiState.Success).data.size)
    }

    @Test
    fun `load surfaces Empty when the role has no projects`() {
        val repository = FakeCareerPathRepository().apply { projectsResult = { emptyList() } }
        val viewModel = CareerPathProjectsViewModel(1, repository)

        assertTrue(viewModel.uiState.value is UiState.Empty)
    }

    @Test
    fun `load surfaces an error message on failure`() {
        val repository = FakeCareerPathRepository().apply { projectsResult = { throw java.io.IOException("down") } }
        val viewModel = CareerPathProjectsViewModel(1, repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }
}
