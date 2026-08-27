package com.aiplacementos.app.ui.onboarding

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.SkillDto
import com.aiplacementos.app.fakes.FakeProfileRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingSkillsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the student's current skills`() {
        val repository = FakeProfileRepository().apply {
            skillsResult = Result.success(listOf(SkillDto(id = 1, name = "Python")))
        }

        val viewModel = OnboardingSkillsViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        assertEquals(listOf("Python"), (state as UiState.Success<List<SkillDto>>).data.map { it.name })
    }

    @Test
    fun `toggling an unselected skill adds it`() {
        val repository = FakeProfileRepository().apply {
            skillsResult = Result.success(emptyList())
            addSkillResult = Result.success(SkillDto(id = 5, name = "SQL"))
        }
        val viewModel = OnboardingSkillsViewModel(repository)

        viewModel.toggleSkill("SQL")

        assertEquals("SQL", repository.lastAddSkillArgs?.first)
    }

    @Test
    fun `toggling an already-selected skill removes it by id`() {
        val repository = FakeProfileRepository().apply {
            skillsResult = Result.success(listOf(SkillDto(id = 7, name = "SQL")))
        }
        val viewModel = OnboardingSkillsViewModel(repository)

        viewModel.toggleSkill("SQL")

        assertEquals(7, repository.lastDeletedSkillId)
    }

    @Test
    fun `a load failure surfaces a connectivity message`() {
        val repository = FakeProfileRepository().apply {
            skillsResult = Result.failure(java.io.IOException("no network"))
        }

        val viewModel = OnboardingSkillsViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Couldn't load your skills. Check your connection.", (state as UiState.Error).message)
    }
}
