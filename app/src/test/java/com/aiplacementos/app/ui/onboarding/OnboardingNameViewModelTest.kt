package com.aiplacementos.app.ui.onboarding

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeProfileRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingNameViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the current profile on init`() {
        val repository = FakeProfileRepository()

        val viewModel = OnboardingNameViewModel(repository)

        assertTrue(viewModel.uiState.value is UiState.Success)
    }

    @Test
    fun `a load failure surfaces a connectivity message`() {
        val repository = FakeProfileRepository().apply {
            profileResult = Result.failure(java.io.IOException("no network"))
        }

        val viewModel = OnboardingNameViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Couldn't load your profile. Check your connection.", (state as UiState.Error).message)
    }

    @Test
    fun `save updates the full name and invokes the callback`() {
        val repository = FakeProfileRepository()
        val viewModel = OnboardingNameViewModel(repository)
        var advanced = false

        viewModel.save("Asha Rao", onSaved = { advanced = true })

        assertEquals("Asha Rao", repository.lastUpdateProfileArgs?.fullName)
        assertTrue(advanced)
    }

    @Test
    fun `a save failure surfaces an error and does not advance`() {
        val repository = FakeProfileRepository().apply {
            updateProfileResult = Result.failure(java.io.IOException("no network"))
        }
        val viewModel = OnboardingNameViewModel(repository)
        var advanced = false

        viewModel.save("Asha Rao", onSaved = { advanced = true })

        assertTrue(viewModel.uiState.value is UiState.Error)
        assertTrue(!advanced)
    }
}
