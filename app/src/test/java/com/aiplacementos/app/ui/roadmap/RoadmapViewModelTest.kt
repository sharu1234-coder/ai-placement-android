package com.aiplacementos.app.ui.roadmap

import com.aiplacementos.app.data.remote.RoadmapDto
import com.aiplacementos.app.fakes.FakeRoadmapRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The lifetime of a roadmap generation's Idempotency-Key.
 *
 * This is the half of Phase 24-B that lives on the client, and the half that is easiest to get
 * subtly wrong: the server deduplicates by `(profile_id, idempotency_key)`, so a key minted per
 * *attempt* rather than per *operation* leaves the mechanism fully in place and doing nothing.
 * Two different arrivals of one intent would look like two intents and both would be generated.
 *
 * So these assert on the keys the repository was handed, not on the rendered state:
 *
 *  - one generation, one key;
 *  - a retry after a failure reuses it, because the failed attempt may have committed before it
 *    lost its reply;
 *  - a deliberate second generation after a success gets a new one, because that is a new
 *    intent and must not be answered with the first roadmap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoadmapViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    // --- operation-key identity (Phase 24-D) ---

    @Test
    fun `changing the duration after a failure is a new operation`() = runTest(dispatcher) {
        // The server fingerprints jd_id and duration_days together, so the 30-day key sent with
        // a 60-day body came back as a 409 -- correct of the server, and useless to the student.
        val repo = repoWithNoRoadmap()
        repo.generateResult = { _, _ -> throw IOException("no connection") }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.generate(30); advanceUntilIdle()
        vm.generate(30); advanceUntilIdle()
        vm.generate(60); advanceUntilIdle()

        assertEquals(3, repo.keys.size)
        assertEquals("a retry of the 30-day generation", repo.keys[0], repo.keys[1])
        assertNotEquals("60 days is a different request", repo.keys[0], repo.keys[2])
    }

    @Test
    fun `two unresolved durations keep their own keys`() = runTest(dispatcher) {
        val repo = repoWithNoRoadmap()
        repo.generateResult = { _, _ -> throw IOException("no connection") }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.generate(30); advanceUntilIdle()
        vm.generate(60); advanceUntilIdle()
        vm.generate(30); advanceUntilIdle()
        vm.generate(60); advanceUntilIdle()

        assertEquals("30 days came back to its own key", repo.keys[0], repo.keys[2])
        assertEquals("and so did 60", repo.keys[1], repo.keys[3])
        assertNotEquals(repo.keys[0], repo.keys[1])
    }

    @Test
    fun `a generation that succeeded consumes only that duration`() = runTest(dispatcher) {
        val repo = repoWithNoRoadmap().apply { generateResult = { _, _ -> roadmap() } }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.generate(30); advanceUntilIdle()
        vm.generate(30); advanceUntilIdle()

        assertNotEquals("a second deliberate generation is a new operation",
                        repo.keys[0], repo.keys[1])
    }

    @Test
    fun `reassess is keyed by the roadmap it reassesses`() = runTest(dispatcher) {
        val repo = repoWithNoRoadmap().apply {
            currentResult = { roadmap(7) }
            reassessResult = { throw IOException("no connection") }
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.reassess(); advanceUntilIdle()
        // A failed reassess leaves an Error, and reassess() only runs from Success -- so the
        // student reloads before trying again, exactly as the screen offers.
        vm.load(); advanceUntilIdle()
        vm.reassess(); advanceUntilIdle()

        assertEquals(2, repo.keys.size)
        assertEquals("one reassessment of roadmap 7", repo.keys[0], repo.keys[1])
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun roadmap(id: Int = 1) = RoadmapDto(
        id = id, jdId = null, durationDays = 30, status = "active", tasks = emptyList(),
    )

    private fun viewModel(repo: FakeRoadmapRepository) =
        RoadmapViewModel(jdId = 0, autoGenerateIfMissing = false, roadmapRepository = repo)

    private fun repoWithNoRoadmap() = FakeRoadmapRepository().apply { currentResult = { null } }

    @Test
    fun `one generation sends one key`() = runTest(dispatcher) {
        val repo = repoWithNoRoadmap().apply { generateResult = { _, _ -> roadmap() } }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.generate(30)
        advanceUntilIdle()

        assertEquals(1, repo.keys.size)
        val key = repo.keys.single()
        assertNotNull("a key was sent", key)
        assertTrue("within the server's limits", key!!.length in 8..64)
    }

    @Test
    fun `a retry after a failure reuses the same key`() = runTest(dispatcher) {
        // The first attempt may have generated the roadmap and lost its reply. A fresh key on
        // the retry would be a second generation -- a second AI call and a plan that supersedes
        // the one the student already has.
        val repo = repoWithNoRoadmap()
        repo.generateResult = { _, _ -> throw IOException("no connection") }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.generate(30)
        advanceUntilIdle()
        vm.generate(30)
        advanceUntilIdle()

        assertEquals(2, repo.keys.size)
        assertEquals("both attempts were the same operation", 1, repo.keys.toSet().size)
    }

    @Test
    fun `a deliberate second generation after success gets a new key`() = runTest(dispatcher) {
        // The other half of the rule. If the key persisted past success, a student asking to
        // regenerate would be handed the roadmap they already had and nothing would change --
        // the mechanism working correctly would look like the feature being broken.
        val repo = repoWithNoRoadmap().apply { generateResult = { _, _ -> roadmap() } }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.generate(30)
        advanceUntilIdle()
        vm.generate(30)
        advanceUntilIdle()

        assertEquals(2, repo.keys.size)
        assertNotEquals("a new intent is a new operation", repo.keys[0], repo.keys[1])
    }
}
