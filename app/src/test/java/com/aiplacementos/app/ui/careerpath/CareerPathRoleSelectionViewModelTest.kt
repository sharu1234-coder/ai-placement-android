package com.aiplacementos.app.ui.careerpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.RoleCatalogEntryDto
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CareerPathRoleSelectionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun repository(
        roles: List<RoleCatalogEntryDto> = listOf(
            RoleCatalogEntryDto("data_analyst", "Data Analyst", "Data & AI", "Turns data into insights."),
        ),
    ) = FakeCareerPathRepository().apply {
        roleCatalogResult = { roles }
        roleCategoriesResult = { listOf("Data & AI", "Technology") }
    }

    @Test
    fun `load surfaces categories and roles`() {
        val viewModel = CareerPathRoleSelectionViewModel(repository())

        val state = viewModel.uiState.value
        assertTrue(state is CareerPathRoleSelectionUiState.Loaded)
        assertEquals(1, (state as CareerPathRoleSelectionUiState.Loaded).roles.size)
        assertTrue(state.categories.contains("Technology"))
    }

    @Test
    fun `selectCustomRole is never rejected as unsupported -- it always calls generate`() {
        val repo = repository().apply {
            generateResult = { _, customRole -> sampleCareerPath(displayRoleName = customRole ?: "Unknown") }
        }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        var generatedPathId: Int? = null
        viewModel.selectCustomRole("Blockchain Developer") { pathId -> generatedPathId = pathId }

        assertEquals("Blockchain Developer", repo.lastGenerateCustomRole)
        assertEquals(1, generatedPathId)
    }

    @Test
    fun `rate limit failure surfaces a specific message and clears busy state`() {
        val repo = repository().apply {
            generateResult = { _, _ -> throw httpException(429) }
        }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Data Analyst") { }

        val state = viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded
        assertFalse(state.isGenerating)
        assertNotNull(state.generationError)
        assertTrue(state.generationError!!.contains("limit"))
    }

    @Test
    fun `network failure surfaces a generic message`() {
        val repo = repository().apply {
            generateResult = { _, _ -> throw java.io.IOException("down") }
        }
        val viewModel = CareerPathRoleSelectionViewModel(repo)

        viewModel.selectCustomRole("Data Analyst") { }

        val state = viewModel.uiState.value as CareerPathRoleSelectionUiState.Loaded
        assertFalse(state.isGenerating)
        assertNotNull(state.generationError)
    }
}
