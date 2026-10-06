package com.aiplacementos.app.ui.arena

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ArenaAttemptStartDto
import com.aiplacementos.app.data.remote.ArenaChallengeDto
import com.aiplacementos.app.fakes.FakeArenaRepository
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

    // --- Idempotency-Key semantics (Phase 24-C) ---

    private fun arena(failing: Boolean) = FakeArenaRepository().apply {
        listChallengesResult = { listOf(sampleChallenge("sql_sprint"), sampleChallenge("dsa_dash")) }
        startAttemptResult = { key ->
            if (failing) throw UnknownHostException("reply lost") else ArenaAttemptStartDto(1, 10, key, 300)
        }
    }

    @Test
    fun `a second tap while the first start is unresolved reuses its key`() {
        val repository = arena(failing = true)
        val viewModel = ArenaListViewModel(repository)

        viewModel.startChallenge("sql_sprint") {}
        viewModel.startChallenge("sql_sprint") {}

        assertEquals("both taps reached the repository", 2, repository.keys.size)
        assertEquals("but they are one logical operation", 1, repository.keys.distinct().size)
        assertNotNull(repository.keys.first())
    }

    @Test
    fun `a successful start consumes its key, so starting it again is a new operation`() {
        val repository = arena(failing = false)
        val viewModel = ArenaListViewModel(repository)

        viewModel.startChallenge("sql_sprint") {}
        viewModel.startChallenge("sql_sprint") {}

        assertEquals(2, repository.keys.distinct().size)
    }

    @Test
    fun `returning to a challenge after trying another one gets its original key back`() {
        // The case a single-slot key field gets wrong: starting DSA would evict SQL's key, and
        // SQL's retry would become a second attempt on a request that may already have
        // committed. Keyed by challenge, the first key comes back.
        val repository = arena(failing = true)
        val viewModel = ArenaListViewModel(repository)

        viewModel.startChallenge("sql_sprint") {}
        viewModel.startChallenge("dsa_dash") {}
        viewModel.startChallenge("sql_sprint") {}

        assertEquals(3, repository.keys.size)
        assertEquals(repository.keys[0], repository.keys[2])
        assertNotEquals(repository.keys[0], repository.keys[1])
    }

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
