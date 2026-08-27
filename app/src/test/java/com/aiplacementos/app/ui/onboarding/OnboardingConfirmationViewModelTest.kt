package com.aiplacementos.app.ui.onboarding

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.PlacementPathDto
import com.aiplacementos.app.fakes.FakePlacementPathRepository
import com.aiplacementos.app.fakes.FakeProfileRepository
import com.aiplacementos.app.fakes.httpException
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun fakePath() = PlacementPathDto(
    id = 1,
    status = "active",
    createdAt = "2026-08-01T00:00:00Z",
    completedTopics = 0,
    totalTopics = 1,
    stages = emptyList(),
    nextBestAction = null,
)

class OnboardingConfirmationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the profile, skills, and roles for the summary`() {
        val profileRepository = FakeProfileRepository()
        val pathRepository = FakePlacementPathRepository()

        val viewModel = OnboardingConfirmationViewModel(profileRepository, pathRepository)

        assertTrue(viewModel.uiState.value is UiState.Success)
    }

    @Test
    fun `creating the roadmap generates it then marks onboarding complete`() {
        val profileRepository = FakeProfileRepository()
        val pathRepository = FakePlacementPathRepository().apply { generateResult = { fakePath() } }
        val viewModel = OnboardingConfirmationViewModel(profileRepository, pathRepository)
        var done = false

        viewModel.createRoadmap(onDone = { done = true })

        assertTrue(done)
    }

    @Test
    fun `a 422 from generate shows the no-relevant-skills message`() {
        val profileRepository = FakeProfileRepository()
        val pathRepository = FakePlacementPathRepository().apply { generateResult = { throw httpException(422) } }
        val viewModel = OnboardingConfirmationViewModel(profileRepository, pathRepository)

        viewModel.createRoadmap(onDone = {})

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertTrue((state as UiState.Error).message.contains("resume", ignoreCase = true))
    }

    @Test
    fun `a network failure during generate surfaces an error and does not advance`() {
        val profileRepository = FakeProfileRepository()
        val pathRepository = FakePlacementPathRepository().apply {
            generateResult = { throw java.io.IOException("timeout") }
        }
        val viewModel = OnboardingConfirmationViewModel(profileRepository, pathRepository)
        var done = false

        viewModel.createRoadmap(onDone = { done = true })

        assertTrue(viewModel.uiState.value is UiState.Error)
        assertTrue(!done)
    }
}
