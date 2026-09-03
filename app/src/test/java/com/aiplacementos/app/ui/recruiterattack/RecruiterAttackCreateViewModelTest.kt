package com.aiplacementos.app.ui.recruiterattack

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ProjectBuildListItemDto
import com.aiplacementos.app.data.remote.RecruiterAttackSessionDto
import com.aiplacementos.app.fakes.FakeProjectBuilderRepository
import com.aiplacementos.app.fakes.FakeRecruiterAttackRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun sampleBuild(id: Int, status: String) = ProjectBuildListItemDto(
    id = id, title = "Build $id", status = status, createdAt = "2026-09-02T10:00:00Z",
)

private fun sampleSession(id: Int = 1) = RecruiterAttackSessionDto(
    id = id, sourceType = "project_build", sourceLabel = "Build 1", status = "in_progress",
    createdAt = "2026-09-02T10:00:00Z", completedAt = null,
)

class RecruiterAttackCreateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads and filters to only completed project builds`() {
        val projectBuilderRepository = FakeProjectBuilderRepository().apply {
            listResult = { listOf(sampleBuild(1, "completed"), sampleBuild(2, "in_progress"), sampleBuild(3, "completed")) }
        }
        val viewModel = RecruiterAttackCreateViewModel(FakeRecruiterAttackRepository(), projectBuilderRepository)

        val state = viewModel.uiState.value as RecruiterAttackCreateUiState.Ready
        assertEquals(listOf(1, 3), state.completedBuilds.map { it.id })
    }

    @Test
    fun `load retries after a failed initial load`() {
        val projectBuilderRepository = FakeProjectBuilderRepository().apply {
            listResult = { error("network down") }
        }
        val viewModel = RecruiterAttackCreateViewModel(FakeRecruiterAttackRepository(), projectBuilderRepository)

        assertTrue(viewModel.uiState.value is RecruiterAttackCreateUiState.Error)

        projectBuilderRepository.listResult = { listOf(sampleBuild(1, "completed")) }
        viewModel.load()

        val state = viewModel.uiState.value as RecruiterAttackCreateUiState.Ready
        assertEquals(listOf(1), state.completedBuilds.map { it.id })
    }

    @Test
    fun `starting from a project succeeds`() {
        val projectBuilderRepository = FakeProjectBuilderRepository().apply {
            listResult = { listOf(sampleBuild(1, "completed")) }
        }
        val recruiterAttackRepository = FakeRecruiterAttackRepository().apply {
            startSessionResult = { _, _, _ -> sampleSession() }
        }
        val viewModel = RecruiterAttackCreateViewModel(recruiterAttackRepository, projectBuilderRepository)

        viewModel.startFromProject(1)

        val state = viewModel.uiState.value
        assertTrue(state is RecruiterAttackCreateUiState.Success)
        assertEquals(1, (state as RecruiterAttackCreateUiState.Success).sessionId)
    }

    @Test
    fun `starting from a blank resume claim surfaces an error without calling the repository`() {
        val projectBuilderRepository = FakeProjectBuilderRepository().apply { listResult = { emptyList() } }
        val recruiterAttackRepository = FakeRecruiterAttackRepository().apply {
            startSessionResult = { _, _, _ -> error("should not be called") }
        }
        val viewModel = RecruiterAttackCreateViewModel(recruiterAttackRepository, projectBuilderRepository)

        viewModel.startFromResumeClaim("   ")

        val state = viewModel.uiState.value as RecruiterAttackCreateUiState.Ready
        assertTrue(state.submitError != null)
    }

    @Test
    fun `a backend rejection surfaces as a submit error without leaving Ready`() {
        val projectBuilderRepository = FakeProjectBuilderRepository().apply { listResult = { emptyList() } }
        val recruiterAttackRepository = FakeRecruiterAttackRepository().apply {
            startSessionResult = { _, _, _ -> throw IllegalStateException("That claim wasn't found on your resume") }
        }
        val viewModel = RecruiterAttackCreateViewModel(recruiterAttackRepository, projectBuilderRepository)

        viewModel.startFromResumeClaim("led a rocket launch")

        val state = viewModel.uiState.value
        assertTrue(state is RecruiterAttackCreateUiState.Ready)
        assertTrue((state as RecruiterAttackCreateUiState.Ready).submitError != null)
        assertTrue(!state.submitting)
    }
}
