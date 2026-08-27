package com.aiplacementos.app.ui.onboarding

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeProfileRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingExperienceLevelViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `save passes the chosen level through and invokes the callback`() {
        val repository = FakeProfileRepository()
        val viewModel = OnboardingExperienceLevelViewModel(repository)
        var advanced = false

        viewModel.save("intermediate", onSaved = { advanced = true })

        assertEquals("intermediate", repository.lastUpdateProfileArgs?.experienceLevel)
        assertTrue(advanced)
    }

    @Test
    fun `a save failure surfaces an error and does not advance`() {
        val repository = FakeProfileRepository().apply {
            updateProfileResult = Result.failure(java.io.IOException("no network"))
        }
        val viewModel = OnboardingExperienceLevelViewModel(repository)
        var advanced = false

        viewModel.save("beginner", onSaved = { advanced = true })

        assertTrue(viewModel.uiState.value is UiState.Error)
        assertTrue(!advanced)
    }
}
