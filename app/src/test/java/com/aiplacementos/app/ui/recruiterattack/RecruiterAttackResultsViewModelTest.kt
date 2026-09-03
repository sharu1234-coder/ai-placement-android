package com.aiplacementos.app.ui.recruiterattack

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.RecruiterAttackResultsDto
import com.aiplacementos.app.fakes.FakeRecruiterAttackRepository
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun sampleResults() = RecruiterAttackResultsDto(
    technicalDefense = 0.8, projectOwnership = 0.75, specificity = 0.7, communication = 0.85,
    overallScore = 0.78, feedback = "Clear and specific.",
)

class RecruiterAttackResultsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows Success with the loaded results`() {
        val repository = FakeRecruiterAttackRepository().apply { getResultsResult = { sampleResults() } }
        val viewModel = RecruiterAttackResultsViewModel(1, repository)

        val state = viewModel.uiState.value as RecruiterAttackResultsUiState.Success
        assertEquals(0.78, state.results.overallScore, 0.0)
    }

    @Test
    fun `load failure surfaces as Error`() {
        val repository = FakeRecruiterAttackRepository().apply {
            getResultsResult = { throw UnknownHostException("no route") }
        }
        val viewModel = RecruiterAttackResultsViewModel(1, repository)

        assertTrue(viewModel.uiState.value is RecruiterAttackResultsUiState.Error)
    }
}
