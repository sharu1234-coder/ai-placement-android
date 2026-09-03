package com.aiplacementos.app.ui.projectbuilder

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.GitHubEvidenceDto
import com.aiplacementos.app.data.remote.ProjectBuildDto
import com.aiplacementos.app.data.remote.ProjectBuildStageDto
import com.aiplacementos.app.fakes.FakeProjectBuilderRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.UnknownHostException

private fun sampleEvidence(repoExists: Boolean = true) = GitHubEvidenceDto(
    repoUrl = "https://github.com/octocat/Hello-World",
    owner = "octocat",
    repoName = "Hello-World",
    repoExists = repoExists,
    primaryLanguage = "Python",
    hasReadme = true,
    stars = 5,
    matchesDeclaredStack = true,
    evidenceLabel = "Public repository found -- well-documented with a detected primary language.",
    checkedAt = "2026-09-02T10:00:00Z",
)

private fun sampleStage(key: String = "understand_problem", status: String = "not_started") = ProjectBuildStageDto(
    stageKey = key, title = "Understand the Problem", orderIndex = 0, status = status, guidanceText = "Guidance text.",
)

private fun sampleBuild(id: Int = 1, status: String = "not_started", stages: List<ProjectBuildStageDto> = listOf(sampleStage())) =
    ProjectBuildDto(
        id = id, title = "Realtime Pipeline", targetRoleName = "Data Engineer", status = status,
        createdAt = "2026-09-02T10:00:00Z", completedAt = null, stackSummary = "Kafka", stages = stages,
    )

class ProjectBuildDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows Success with the loaded build`() {
        val repository = FakeProjectBuilderRepository().apply { getBuildResult = { sampleBuild() } }
        val viewModel = ProjectBuildDetailViewModel(1, repository)

        val state = viewModel.uiState.value
        assertTrue(state is ProjectBuildDetailUiState.Success)
    }

    @Test
    fun `starting a stage reloads the build on success`() {
        val repository = FakeProjectBuilderRepository().apply {
            getBuildResult = { sampleBuild(stages = listOf(sampleStage(status = "in_progress"))) }
            startStageResult = { _, key -> sampleStage(key, "in_progress") }
        }
        val viewModel = ProjectBuildDetailViewModel(1, repository)

        viewModel.startStage("understand_problem")

        val state = viewModel.uiState.value as ProjectBuildDetailUiState.Success
        assertEquals("in_progress", state.build.stages[0].status)
        assertNull(viewModel.actionMessage.value)
    }

    @Test
    fun `starting a locked stage surfaces an action message without blanking the screen`() {
        val repository = FakeProjectBuilderRepository().apply {
            getBuildResult = { sampleBuild() }
            startStageResult = { _, _ -> throw httpException(409) }
        }
        val viewModel = ProjectBuildDetailViewModel(1, repository)

        viewModel.startStage("architecture")

        assertTrue(viewModel.uiState.value is ProjectBuildDetailUiState.Success)
        assertTrue(viewModel.actionMessage.value != null)
    }

    @Test
    fun `completing a stage reloads the build on success`() {
        val repository = FakeProjectBuilderRepository().apply {
            getBuildResult = { sampleBuild(stages = listOf(sampleStage(status = "completed"))) }
            completeStageResult = { _, key, _ -> sampleStage(key, "completed") }
        }
        val viewModel = ProjectBuildDetailViewModel(1, repository)

        viewModel.completeStage("understand_problem", "My notes")

        val state = viewModel.uiState.value as ProjectBuildDetailUiState.Success
        assertEquals("completed", state.build.stages[0].status)
    }

    @Test
    fun `clearActionMessage resets the transient message`() {
        val repository = FakeProjectBuilderRepository().apply {
            getBuildResult = { sampleBuild() }
            startStageResult = { _, _ -> throw UnknownHostException("no route") }
        }
        val viewModel = ProjectBuildDetailViewModel(1, repository)

        viewModel.startStage("architecture")
        assertTrue(viewModel.actionMessage.value != null)

        viewModel.clearActionMessage()
        assertNull(viewModel.actionMessage.value)
    }

    @Test
    fun `checking github reloads the build with the returned evidence`() {
        val repository = FakeProjectBuilderRepository().apply {
            getBuildResult = { sampleBuild().copy(githubEvidence = sampleEvidence()) }
            checkGithubResult = { _, _ -> sampleEvidence() }
        }
        val viewModel = ProjectBuildDetailViewModel(1, repository)

        viewModel.checkGithub("https://github.com/octocat/Hello-World")

        val state = viewModel.uiState.value as ProjectBuildDetailUiState.Success
        assertEquals(true, state.build.githubEvidence?.repoExists)
        assertNull(viewModel.actionMessage.value)
    }

    @Test
    fun `checking github with a blank url surfaces an action message without calling the repository`() {
        val repository = FakeProjectBuilderRepository().apply {
            getBuildResult = { sampleBuild() }
            checkGithubResult = { _, _ -> error("should not be called") }
        }
        val viewModel = ProjectBuildDetailViewModel(1, repository)

        viewModel.checkGithub("   ")

        assertTrue(viewModel.actionMessage.value != null)
    }

    @Test
    fun `an invalid repo url from the backend surfaces as an action message`() {
        val repository = FakeProjectBuilderRepository().apply {
            getBuildResult = { sampleBuild() }
            checkGithubResult = { _, _ -> throw httpException(422) }
        }
        val viewModel = ProjectBuildDetailViewModel(1, repository)

        viewModel.checkGithub("not a url")

        assertTrue(viewModel.uiState.value is ProjectBuildDetailUiState.Success)
        assertTrue(viewModel.actionMessage.value != null)
    }

    @Test
    fun `load failure surfaces as Error`() {
        val repository = FakeProjectBuilderRepository().apply { getBuildResult = { throw UnknownHostException("no route") } }
        val viewModel = ProjectBuildDetailViewModel(1, repository)

        assertTrue(viewModel.uiState.value is ProjectBuildDetailUiState.Error)
    }
}
