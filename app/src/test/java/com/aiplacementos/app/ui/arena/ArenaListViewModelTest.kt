package com.aiplacementos.app.ui.arena

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ArenaAttemptStartDto
import com.aiplacementos.app.data.remote.ArenaChallengeDto
import com.aiplacementos.app.fakes.FakeArenaRepository
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun sampleChallenge(key: String = "sql_sprint") = ArenaChallengeDto(
    key = key, title = "SQL Sprint", category = "sql", description = "Five timed SQL questions.",
    questionCount = 5, timeLimitSeconds = 300, personalBestScore = null,
)

class ArenaListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows Success with the loaded challenges`() {
        val repository = FakeArenaRepository().apply { listChallengesResult = { listOf(sampleChallenge()) } }
        val viewModel = ArenaListViewModel(repository)

        val state = viewModel.uiState.value as ArenaListUiState.Success
        assertEquals(1, state.challenges.size)
    }

    @Test
    fun `network failure surfaces as a network Error`() {
        val repository = FakeArenaRepository().apply { listChallengesResult = { throw UnknownHostException("no route") } }
        val viewModel = ArenaListViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is ArenaListUiState.Error)
        assertTrue((state as ArenaListUiState.Error).isNetworkError)
    }

    @Test
    fun `starting a challenge calls onStarted with the start response`() {
        val repository = FakeArenaRepository().apply {
            listChallengesResult = { listOf(sampleChallenge()) }
            startAttemptResult = { key -> ArenaAttemptStartDto(1, 10, key, 300) }
        }
        val viewModel = ArenaListViewModel(repository)

        var started: ArenaAttemptStartDto? = null
        viewModel.startChallenge("sql_sprint") { started = it }

        assertEquals(1, started?.arenaAttemptId)
        val state = viewModel.uiState.value as ArenaListUiState.Success
        assertNull(state.startError)
    }

    @Test
    fun `a failed start surfaces an error without losing the challenge list`() {
        val repository = FakeArenaRepository().apply {
            listChallengesResult = { listOf(sampleChallenge()) }
            startAttemptResult = { _ -> throw UnknownHostException("no route") }
        }
        val viewModel = ArenaListViewModel(repository)

        viewModel.startChallenge("sql_sprint") { }

        val state = viewModel.uiState.value as ArenaListUiState.Success
        assertTrue(state.startError != null)
        assertEquals(1, state.challenges.size)
        assertNull(state.startingKey)
    }
}
