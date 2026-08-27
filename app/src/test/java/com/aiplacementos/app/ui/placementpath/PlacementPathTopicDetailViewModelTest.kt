package com.aiplacementos.app.ui.placementpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.PlacementPathDto
import com.aiplacementos.app.data.remote.PlacementPathStageDto
import com.aiplacementos.app.data.remote.PlacementPathTopicDto
import com.aiplacementos.app.fakes.FakePlacementPathRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun topic(id: Int, prerequisiteTopicId: Int? = null, status: String = "not_started") = PlacementPathTopicDto(
    id = id,
    stage = "core_technical",
    trackKey = "sql",
    topicKey = "topic_$id",
    title = "Topic $id",
    whyItMatters = "Because.",
    skillTag = "Skill $id",
    status = status,
    priority = "medium",
    prerequisiteTopicId = prerequisiteTopicId,
    hasPractice = true,
    completedVia = null,
)

private fun pathOf(vararg topics: PlacementPathTopicDto) = PlacementPathDto(
    id = 1,
    status = "active",
    createdAt = "2026-08-01T00:00:00Z",
    completedTopics = topics.count { it.status == "completed" },
    totalTopics = topics.size,
    stages = listOf(PlacementPathStageDto(stage = "core_technical", topics = topics.toList())),
    nextBestAction = null,
)

class PlacementPathTopicDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the requested topic and resolves its prerequisite`() {
        val prerequisite = topic(1, status = "completed")
        val target = topic(2, prerequisiteTopicId = 1)
        val repository = FakePlacementPathRepository().apply {
            currentResult = { pathOf(prerequisite, target) }
        }
        val viewModel = PlacementPathTopicDetailViewModel(topicId = 2, repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        assertEquals(2, (state as UiState.Success).data.id)
        assertEquals(1, viewModel.prerequisite.value?.id)
        assertEquals("completed", viewModel.prerequisite.value?.status)
    }

    @Test
    fun `has no prerequisite when the topic is first in its track`() {
        val target = topic(1, prerequisiteTopicId = null)
        val repository = FakePlacementPathRepository().apply {
            currentResult = { pathOf(target) }
        }
        val viewModel = PlacementPathTopicDetailViewModel(topicId = 1, repository)

        assertNull(viewModel.prerequisite.value)
    }

    @Test
    fun `shows Empty state when the topic id is not found`() {
        val repository = FakePlacementPathRepository().apply {
            currentResult = { pathOf(topic(1)) }
        }
        val viewModel = PlacementPathTopicDetailViewModel(topicId = 999, repository)

        assertEquals(UiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `practice invokes onReady with the blueprint id exactly once`() {
        val target = topic(1)
        val repository = FakePlacementPathRepository().apply {
            currentResult = { pathOf(target) }
            practiceResult = { 42 }
        }
        val viewModel = PlacementPathTopicDetailViewModel(topicId = 1, repository)

        val received = mutableListOf<Int>()
        viewModel.practice(onReady = { received.add(it) })

        assertEquals(listOf(42), received)
    }

    @Test
    fun `a practice failure surfaces an error instead of calling onReady`() {
        val target = topic(1)
        val repository = FakePlacementPathRepository().apply {
            currentResult = { pathOf(target) }
            practiceResult = { throw java.io.IOException("boom") }
        }
        val viewModel = PlacementPathTopicDetailViewModel(topicId = 1, repository)

        val received = mutableListOf<Int>()
        viewModel.practice(onReady = { received.add(it) })

        assertTrue(received.isEmpty())
        assertTrue(viewModel.uiState.value is UiState.Error)
    }

    @Test
    fun `markComplete updates the topic to completed`() {
        val target = topic(1, status = "in_progress")
        val repository = FakePlacementPathRepository().apply {
            currentResult = { pathOf(target) }
            markCompleteResult = { topic(1, status = "completed") }
        }
        val viewModel = PlacementPathTopicDetailViewModel(topicId = 1, repository)

        viewModel.markComplete()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        assertEquals("completed", (state as UiState.Success).data.status)
    }
}
