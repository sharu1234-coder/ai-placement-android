package com.aiplacementos.app.ui.projectbuilder

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ProjectBuildDto
import com.aiplacementos.app.fakes.FakeProjectBuilderRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.UnknownHostException

private fun sampleBuild(id: Int = 42, title: String = "Realtime Pipeline") = ProjectBuildDto(
    id = id, title = title, targetRoleName = "Data Engineer", status = "not_started",
    createdAt = "2026-09-02T10:00:00Z", completedAt = null, stackSummary = "Kafka", stages = emptyList(),
)

class ProjectBuildCreateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `blank title is rejected without calling the repository`() {
        val repository = FakeProjectBuilderRepository()
        val viewModel = ProjectBuildCreateViewModel(repository)

        viewModel.create("   ", "Kafka")

        val state = viewModel.uiState.value
        assertTrue(state is ProjectBuildCreateUiState.Error)
    }

    @Test
    fun `successful creation reaches Success with the new build id`() {
        val repository = FakeProjectBuilderRepository().apply {
            createResult = { title, _ -> sampleBuild(id = 7, title = title) }
        }
        val viewModel = ProjectBuildCreateViewModel(repository)

        viewModel.create("Realtime Pipeline", "Kafka, Flink")

        val state = viewModel.uiState.value
        assertTrue(state is ProjectBuildCreateUiState.Success)
        assertEquals(7, (state as ProjectBuildCreateUiState.Success).buildId)
    }

    @Test
    fun `repository failure surfaces as Error`() {
        val repository = FakeProjectBuilderRepository().apply {
            createResult = { _, _ -> throw UnknownHostException("no route") }
        }
        val viewModel = ProjectBuildCreateViewModel(repository)

        viewModel.create("Realtime Pipeline", "")

        assertTrue(viewModel.uiState.value is ProjectBuildCreateUiState.Error)
    }

    @Test
    fun `blank optional stack summary is sent as null`() {
        var receivedStack: String? = "not set"
        val repository = FakeProjectBuilderRepository().apply {
            createResult = { title, stack -> receivedStack = stack; sampleBuild(title = title) }
        }
        val viewModel = ProjectBuildCreateViewModel(repository)

        viewModel.create("Realtime Pipeline", "   ")

        assertEquals(null, receivedStack)
    }
}
