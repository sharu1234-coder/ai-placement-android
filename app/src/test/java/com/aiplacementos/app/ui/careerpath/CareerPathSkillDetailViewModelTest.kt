package com.aiplacementos.app.ui.careerpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CareerPathSkillDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load finds the matching skill item within the path`() {
        val repository = FakeCareerPathRepository().apply { pathResult = { sampleCareerPath() } }
        val viewModel = CareerPathSkillDetailViewModel(1, 1, repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<*>)
        assertEquals("SQL Basics", (state as UiState.Success).data.item.title)
    }

    @Test
    fun `load surfaces an error when the skill item id doesn't exist in the path`() {
        val repository = FakeCareerPathRepository().apply { pathResult = { sampleCareerPath() } }
        val viewModel = CareerPathSkillDetailViewModel(1, 999, repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }

    @Test
    fun `markComplete reloads the path with the item's new status`() {
        val repository = FakeCareerPathRepository().apply {
            pathResult = { sampleCareerPath() }
            markSkillItemResult = { _, _ -> sampleSkillItem(status = "completed") }
        }
        val viewModel = CareerPathSkillDetailViewModel(1, 1, repository)

        viewModel.markComplete()

        assertEquals(1, repository.lastMarkedSkillItemId)
    }

    @Test
    fun `practice fires the one-shot callback with the blueprint id`() {
        val repository = FakeCareerPathRepository().apply {
            pathResult = { sampleCareerPath() }
            practiceResult = { _ -> 77 }
        }
        val viewModel = CareerPathSkillDetailViewModel(1, 1, repository)

        var receivedBlueprintId: Int? = null
        viewModel.practice { id -> receivedBlueprintId = id }

        assertEquals(77, receivedBlueprintId)
        assertEquals(1, repository.lastPracticedItemId)
    }

    @Test
    fun `practice failure never invokes the callback`() {
        val repository = FakeCareerPathRepository().apply {
            pathResult = { sampleCareerPath() }
            practiceResult = { _ -> throw java.io.IOException("down") }
        }
        val viewModel = CareerPathSkillDetailViewModel(1, 1, repository)

        var receivedBlueprintId: Int? = null
        viewModel.practice { id -> receivedBlueprintId = id }

        assertNull(receivedBlueprintId)
    }
}
