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

    // --- Idempotency-Key semantics (Phase 24-C) ---

    @Test
    fun `a skill whose add failed is retried as the same operation`() {
        val repository = FakeProfileRepository().apply {
            skillsResult = Result.success(emptyList())
            addSkillResult = Result.failure(java.net.UnknownHostException("reply lost"))
        }
        val viewModel = OnboardingSkillsViewModel(repository)

        viewModel.toggleSkill("Python")
        // A failed add turns this screen into an Error, and toggleSkill does nothing unless the
        // list is on screen -- so reloading is how the student actually gets back to retrying.
        viewModel.load()
        viewModel.toggleSkill("Python")

        assertEquals(2, repository.keys.size)
        assertEquals("one skill, one operation", 1, repository.keys.distinct().size)
    }

    @Test
    fun `each skill added gets its own key, and re-adding one is a new operation`() {
        val repository = FakeProfileRepository().apply {
            skillsResult = Result.success(emptyList())
            addSkillResult = Result.success(SkillDto(id = 3, name = "Python"))
        }
        val viewModel = OnboardingSkillsViewModel(repository)

        viewModel.toggleSkill("Python")
        viewModel.toggleSkill("SQL")
        viewModel.toggleSkill("Python")

        assertEquals(3, repository.keys.size)
        assertEquals("each add succeeded, so each is its own operation", 3, repository.keys.distinct().size)
    }

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
        assertEquals("Couldn't reach the server. Check your connection and try again.", (state as UiState.Error).message)
    }
}
