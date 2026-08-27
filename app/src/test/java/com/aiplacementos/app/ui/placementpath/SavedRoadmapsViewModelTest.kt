package com.aiplacementos.app.ui.placementpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.PlacementPathDto
import com.aiplacementos.app.fakes.FakePlacementPathRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun path(id: Int, status: String) = PlacementPathDto(
    id = id,
    status = status,
    createdAt = "2026-08-0${id}T00:00:00+00:00",
    completedTopics = 1,
    totalTopics = 4,
    stages = emptyList(),
    nextBestAction = null,
)

class SavedRoadmapsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `no saved roadmaps yields an empty state`() {
        val repository = FakePlacementPathRepository().apply { historyResult = { emptyList() } }
        val viewModel = SavedRoadmapsViewModel(repository)

        assertTrue(viewModel.uiState.value is UiState.Empty)
    }

    @Test
    fun `saved roadmaps include both active and superseded entries`() {
        val repository = FakePlacementPathRepository().apply {
            historyResult = { listOf(path(2, "active"), path(1, "superseded")) }
        }
        val viewModel = SavedRoadmapsViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        val paths = (state as UiState.Success<List<PlacementPathDto>>).data
        assertEquals(listOf(2, 1), paths.map { it.id })
        assertEquals(setOf("active", "superseded"), paths.map { it.status }.toSet())
    }

    @Test
    fun `a load failure surfaces an error state`() {
        val repository = FakePlacementPathRepository().apply {
            historyResult = { throw java.io.IOException("boom") }
        }
        val viewModel = SavedRoadmapsViewModel(repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }
}
