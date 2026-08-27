package com.aiplacementos.app.ui.careerpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CareerPathProjectDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load finds the matching project`() {
        val repository = FakeCareerPathRepository().apply {
            projectsResult = { listOf(sampleProject(id = 1), sampleProject(id = 2, title = "Churn Analysis")) }
        }
        val viewModel = CareerPathProjectDetailViewModel(1, 2, repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<*>)
        assertEquals("Churn Analysis", (state as UiState.Success).data.title)
    }

    @Test
    fun `load surfaces an error when the project id doesn't exist`() {
        val repository = FakeCareerPathRepository().apply { projectsResult = { listOf(sampleProject(id = 1)) } }
        val viewModel = CareerPathProjectDetailViewModel(1, 999, repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }

    @Test
    fun `setStatus reloads the project with the new status`() {
        val repository = FakeCareerPathRepository().apply {
            projectsResult = { listOf(sampleProject(id = 1, status = "in_progress")) }
            markProjectResult = { _, status -> sampleProject(id = 1, status = status) }
        }
        val viewModel = CareerPathProjectDetailViewModel(1, 1, repository)

        viewModel.setStatus("completed")

        assertEquals(1, repository.lastMarkedProjectId)
    }
}
