package com.aiplacementos.app.ui.projectbuilder

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ProjectProofDto
import com.aiplacementos.app.fakes.FakeProjectBuilderRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.UnknownHostException

private fun sampleProof() = ProjectProofDto(
    resumeBullet = "Built a real-time pipeline.",
    readmeMarkdown = "# Realtime Pipeline",
    explanation60s = "60s explanation.",
    explanation5min = "5min explanation.",
    defenseQuestions = listOf("Why Kafka?"),
)

class ProjectProofViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows Success with the returned proof`() {
        val repository = FakeProjectBuilderRepository().apply { proofResult = { sampleProof() } }
        val viewModel = ProjectProofViewModel(1, repository)

        val state = viewModel.uiState.value
        assertTrue(state is ProjectProofUiState.Success)
        assertEquals(1, (state as ProjectProofUiState.Success).proof.defenseQuestions.size)
    }

    @Test
    fun `not-ready proof (409) surfaces as a non-network error`() {
        val repository = FakeProjectBuilderRepository().apply { proofResult = { throw httpException(409) } }
        val viewModel = ProjectProofViewModel(1, repository)

        val state = viewModel.uiState.value as ProjectProofUiState.Error
        assertFalse(state.isNetworkError)
    }

    @Test
    fun `genuine connectivity failure is flagged as a network error`() {
        val repository = FakeProjectBuilderRepository().apply { proofResult = { throw UnknownHostException("no route") } }
        val viewModel = ProjectProofViewModel(1, repository)

        val state = viewModel.uiState.value as ProjectProofUiState.Error
        assertTrue(state.isNetworkError)
    }
}
