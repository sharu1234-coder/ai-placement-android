package com.aiplacementos.app.ui.recruiterattack

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.RecruiterAttackSessionListItemDto
import com.aiplacementos.app.fakes.FakeRecruiterAttackRepository
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun sampleSession(id: Int = 1, status: String = "completed", overallScore: Double? = 0.8) =
    RecruiterAttackSessionListItemDto(
        id = id, sourceType = "project_build", sourceLabel = "Realtime Pipeline", status = status,
        createdAt = "2026-09-02T10:00:00Z", completedAt = if (status == "completed") "2026-09-02T10:10:00Z" else null,
        overallScore = overallScore,
    )

class RecruiterAttackListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows Success with the loaded sessions`() {
        val repository = FakeRecruiterAttackRepository().apply {
            listSessionsResult = { listOf(sampleSession()) }
        }
        val viewModel = RecruiterAttackListViewModel(repository)

        val state = viewModel.uiState.value as RecruiterAttackListUiState.Success
        assertEquals(1, state.sessions.size)
    }

    @Test
    fun `network failure surfaces as a network Error`() {
        val repository = FakeRecruiterAttackRepository().apply {
            listSessionsResult = { throw UnknownHostException("no route") }
        }
        val viewModel = RecruiterAttackListViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is RecruiterAttackListUiState.Error)
        assertTrue((state as RecruiterAttackListUiState.Error).isNetworkError)
    }
}
