package com.aiplacementos.app.ui.onboarding

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeProfileRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingEducationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `save passes college, degree, branch, and graduation year through`() {
        val repository = FakeProfileRepository()
        val viewModel = OnboardingEducationViewModel(repository)
        var advanced = false

        viewModel.save("IIT Bombay", "B.Tech", "Computer Science", 2027, onSaved = { advanced = true })

        val args = repository.lastUpdateProfileArgs
        assertEquals("IIT Bombay", args?.college)
        assertEquals("B.Tech", args?.degree)
        assertEquals("Computer Science", args?.branch)
        assertEquals(2027, args?.graduationYear)
        assertTrue(advanced)
    }

    @Test
    fun `a save failure surfaces an error and does not advance`() {
        val repository = FakeProfileRepository().apply {
            updateProfileResult = Result.failure(java.io.IOException("no network"))
        }
        val viewModel = OnboardingEducationViewModel(repository)
        var advanced = false

        viewModel.save("IIT Bombay", "B.Tech", "CS", null, onSaved = { advanced = true })

        assertTrue(viewModel.uiState.value is UiState.Error)
        assertTrue(!advanced)
    }
}
