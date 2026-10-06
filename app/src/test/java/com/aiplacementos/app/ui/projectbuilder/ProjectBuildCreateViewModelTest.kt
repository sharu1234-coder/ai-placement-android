package com.aiplacementos.app.ui.projectbuilder

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ProjectBuildDto
import com.aiplacementos.app.fakes.FakeProjectBuilderRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    // --- operation-key identity (Phase 24-D) ---

    private fun failingBuilder() = FakeProjectBuilderRepository().apply {
        createResult = { _, _ -> throw java.net.UnknownHostException("reply lost") }
    }

    @Test
    fun `retrying the same build reuses its key, and an edited build does not`() {
        val repository = failingBuilder()
        val viewModel = ProjectBuildCreateViewModel(repository)

        viewModel.create("Rate limiter", "Kotlin, Redis")
        viewModel.create("Rate limiter", "Kotlin, Redis")
        viewModel.create("Rate limiter v2", "Kotlin, Redis")

        assertEquals(3, repository.keys.size)
        assertEquals("the retry is the same operation", repository.keys[0], repository.keys[1])
        assertNotEquals("the edited title is a new one", repository.keys[0], repository.keys[2])
    }

    @Test
    fun `the stack summary is part of the identity, because the server fingerprints it`() {
        val repository = failingBuilder()
        val viewModel = ProjectBuildCreateViewModel(repository)

        viewModel.create("Rate limiter", "Kotlin, Redis")
        viewModel.create("Rate limiter", "Kotlin, Redis, Postgres")

        assertNotEquals(repository.keys[0], repository.keys[1])
    }

    @Test
    fun `two unresolved builds keep their own keys, and neither evicts the other`() {
        val repository = failingBuilder()
        val viewModel = ProjectBuildCreateViewModel(repository)

        viewModel.create("A", "Kotlin")
        viewModel.create("B", "Python")
        viewModel.create("A", "Kotlin")
        viewModel.create("B", "Python")

        assertEquals(listOf(repository.keys[0], repository.keys[1]),
                     listOf(repository.keys[2], repository.keys[3]))
        assertNotEquals(repository.keys[0], repository.keys[1])
    }

    @Test
    fun `a build that succeeded consumes only its own identity`() {
        val repository = FakeProjectBuilderRepository().apply {
            createResult = { _, _ -> sampleBuild() }
        }
        val viewModel = ProjectBuildCreateViewModel(repository)

        viewModel.create("A", "Kotlin")
        viewModel.create("A", "Kotlin")

        assertNotEquals("the second deliberate create is a new operation",
                        repository.keys[0], repository.keys[1])
    }

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
