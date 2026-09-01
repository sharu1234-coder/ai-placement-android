package com.aiplacementos.app.ui.placementpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.NextBestActionDto
import com.aiplacementos.app.data.remote.PlacementPathDto
import com.aiplacementos.app.data.remote.PlacementPathStageDto
import com.aiplacementos.app.data.remote.PlacementPathTopicDto
import com.aiplacementos.app.fakes.FakePlacementPathRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private fun topic(
    id: Int,
    status: String = "not_started",
    priority: String = "medium",
    trackKey: String = "sql",
) = PlacementPathTopicDto(
    id = id,
    stage = "core_technical",
    trackKey = trackKey,
    topicKey = "topic_$id",
    title = "Topic $id",
    whyItMatters = "Because.",
    skillTag = "Skill $id",
    status = status,
    priority = priority,
    prerequisiteTopicId = null,
    hasPractice = true,
    completedVia = null,
)

private fun pathDto(topics: List<PlacementPathTopicDto>, id: Int = 1) = PlacementPathDto(
    id = id,
    status = "active",
    createdAt = "2026-08-01T00:00:00Z",
    completedTopics = topics.count { it.status == "completed" },
    totalTopics = topics.size,
    stages = listOf(PlacementPathStageDto(stage = "core_technical", topics = topics)),
    nextBestAction = NextBestActionDto(topicId = topics.first().id, title = topics.first().title, reason = "Start here."),
)

class PlacementPathViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads NoPath state when getCurrent returns null`() {
        val repository = FakePlacementPathRepository().apply {
            currentResult = { null }
        }
        val viewModel = PlacementPathViewModel(repository)

        assertEquals(PlacementPathUiState.NoPath, viewModel.uiState.value)
    }

    @Test
    fun `loads Success state with stages and topics when a path exists`() {
        val path = pathDto(listOf(topic(1)))
        val repository = FakePlacementPathRepository().apply {
            currentResult = { path }
        }
        val viewModel = PlacementPathViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is PlacementPathUiState.Success)
        assertEquals(1, (state as PlacementPathUiState.Success).path.totalTopics)
    }

    @Test
    fun `network failure on load shows Error state`() {
        val repository = FakePlacementPathRepository().apply {
            currentResult = { throw RuntimeException("boom") }
        }
        val viewModel = PlacementPathViewModel(repository)

        assertTrue(viewModel.uiState.value is PlacementPathUiState.Error)
    }

    // Real-device finding: load() previously caught every Exception identically and always
    // showed "Check your connection", even for a 401 (expired session) or a 500 (server
    // bug) -- indistinguishable from an actual dropped Wi-Fi connection. These four cases
    // pin down the classification now expected of load()'s catch block.

    @Test
    fun `genuine connectivity failure on load is flagged as a network error`() {
        val repository = FakePlacementPathRepository().apply {
            currentResult = { throw UnknownHostException("no route to host") }
        }
        val viewModel = PlacementPathViewModel(repository)

        val state = viewModel.uiState.value as PlacementPathUiState.Error
        assertTrue(state.isNetworkError)
        assertTrue(state.message.contains("connection", ignoreCase = true))
    }

    @Test
    fun `timeout on load gets a distinct message, not the connectivity message`() {
        val repository = FakePlacementPathRepository().apply {
            currentResult = { throw SocketTimeoutException("timeout") }
        }
        val viewModel = PlacementPathViewModel(repository)

        val state = viewModel.uiState.value as PlacementPathUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("too long", ignoreCase = true))
    }

    @Test
    fun `expired session on load shows a sign-in message, not a connectivity message`() {
        val repository = FakePlacementPathRepository().apply {
            currentResult = { throw httpException(401) }
        }
        val viewModel = PlacementPathViewModel(repository)

        val state = viewModel.uiState.value as PlacementPathUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("session", ignoreCase = true))
    }

    @Test
    fun `server error on load is reported as a server problem, not a connectivity problem`() {
        val repository = FakePlacementPathRepository().apply {
            currentResult = { throw httpException(500) }
        }
        val viewModel = PlacementPathViewModel(repository)

        val state = viewModel.uiState.value as PlacementPathUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("our server", ignoreCase = true))
    }

    @Test
    fun `generate transitions to Success on completion`() {
        val path = pathDto(listOf(topic(1)))
        val repository = FakePlacementPathRepository().apply {
            currentResult = { null }
            generateResult = { path }
        }
        val viewModel = PlacementPathViewModel(repository)
        assertEquals(PlacementPathUiState.NoPath, viewModel.uiState.value)

        viewModel.generate()

        val state = viewModel.uiState.value
        assertTrue(state is PlacementPathUiState.Success)
        assertEquals(path.id, (state as PlacementPathUiState.Success).path.id)
    }

    @Test
    fun `generate on 422 shows the no-relevant-skills message`() {
        val repository = FakePlacementPathRepository().apply {
            currentResult = { null }
            generateResult = { throw httpException(422) }
        }
        val viewModel = PlacementPathViewModel(repository)

        viewModel.generate()

        val state = viewModel.uiState.value
        assertTrue(state is PlacementPathUiState.Error)
        assertTrue((state as PlacementPathUiState.Error).message.contains("resume", ignoreCase = true))
    }

    @Test
    fun `startTopic patches only the affected topic in local state`() {
        val original = listOf(topic(1, status = "not_started"), topic(2, status = "locked"))
        val path = pathDto(original)
        val updatedTopic1 = topic(1, status = "in_progress")
        val repository = FakePlacementPathRepository().apply {
            currentResult = { path }
            startTopicResult = { updatedTopic1 }
        }
        val viewModel = PlacementPathViewModel(repository)

        viewModel.startTopic(1)

        val state = viewModel.uiState.value as PlacementPathUiState.Success
        val topics = state.path.stages.flatMap { it.topics }
        assertEquals("in_progress", topics.first { it.id == 1 }.status)
        assertEquals("locked", topics.first { it.id == 2 }.status) // untouched
    }

    @Test
    fun `markComplete reloads the full path instead of patching locally`() {
        val original = listOf(topic(1, status = "in_progress"), topic(2, status = "locked"))
        var loadCallCount = 0
        val repository = FakePlacementPathRepository().apply {
            currentResult = {
                loadCallCount++
                if (loadCallCount == 1) {
                    pathDto(original)
                } else {
                    // Second load reflects the server-side unlock cascade.
                    pathDto(listOf(topic(1, status = "completed"), topic(2, status = "not_started")))
                }
            }
            markCompleteResult = { topic(1, status = "completed") }
        }
        val viewModel = PlacementPathViewModel(repository)
        assertEquals(1, loadCallCount)

        viewModel.markComplete(1)

        assertEquals(2, loadCallCount)
        val state = viewModel.uiState.value as PlacementPathUiState.Success
        val topics = state.path.stages.flatMap { it.topics }
        assertEquals("not_started", topics.first { it.id == 2 }.status) // unlocked, not stale "locked"
    }

    @Test
    fun `PlacementPathViewModel has no dependency on CareerPathRepository -- structurally unaffected by Career Path`() {
        // Reciprocal of CareerPathDetailViewModelTest's independence check -- Roadmap flows
        // must stay correct whether or not the student ever touches Career Path, and this is
        // enforced at the type level (this class has no reference to call), not just asserted
        // behaviorally.
        val constructorParamTypeNames = PlacementPathViewModel::class.java.declaredConstructors
            .flatMap { it.parameterTypes.toList() }
            .map { it.name }
        assertFalse(constructorParamTypeNames.any { it.contains("CareerPath") })
    }
}
