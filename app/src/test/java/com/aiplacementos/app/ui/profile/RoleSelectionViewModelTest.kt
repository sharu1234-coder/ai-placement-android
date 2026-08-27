package com.aiplacementos.app.ui.profile

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.fakes.FakeProfileRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RoleSelectionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the role catalog on init`() {
        val repository = FakeProfileRepository().apply {
            rolesResult = Result.success(listOf(RoleDto(id = 1, name = "SDE", category = "Engineering")))
        }

        val viewModel = RoleSelectionViewModel(repository)

        assertTrue(viewModel.uiState.value is UiState.Success)
    }

    @Test
    fun `primary mode allows custom role, secondary mode does not`() {
        val repository = FakeProfileRepository()

        assertTrue(RoleSelectionViewModel(repository, isSecondary = false).allowCustomRole)
        assertFalse(RoleSelectionViewModel(repository, isSecondary = true).allowCustomRole)
    }

    @Test
    fun `selectRole in primary mode calls selectRole with the role id`() {
        val repository = FakeProfileRepository()
        val viewModel = RoleSelectionViewModel(repository, isSecondary = false)
        var selected = false

        viewModel.selectRole(42, onSelected = { selected = true })

        assertEquals(42, repository.lastSelectRoleArgs?.first)
        assertTrue(selected)
    }

    @Test
    fun `selectRole in secondary mode calls selectSecondaryRole instead`() {
        val repository = FakeProfileRepository()
        val viewModel = RoleSelectionViewModel(repository, isSecondary = true)
        var selected = false

        viewModel.selectRole(42, onSelected = { selected = true })

        assertEquals(42, repository.lastSelectSecondaryRoleId)
        assertTrue(selected)
    }

    @Test
    fun `selectCustomRole calls selectRole with a null role id and the typed text`() {
        val repository = FakeProfileRepository()
        val viewModel = RoleSelectionViewModel(repository, isSecondary = false)
        var selected = false

        viewModel.selectCustomRole("Bioinformatics Engineer", onSelected = { selected = true })

        assertEquals(null, repository.lastSelectRoleArgs?.first)
        assertEquals("Bioinformatics Engineer", repository.lastSelectRoleArgs?.second)
        assertTrue(selected)
    }

    @Test
    fun `selectCustomRole is a no-op in secondary mode`() {
        val repository = FakeProfileRepository()
        val viewModel = RoleSelectionViewModel(repository, isSecondary = true)
        var selected = false

        viewModel.selectCustomRole("Bioinformatics Engineer", onSelected = { selected = true })

        assertEquals(null, repository.lastSelectRoleArgs)
        assertFalse(selected)
    }

    @Test
    fun `a role selection failure surfaces a connectivity message`() {
        val repository = FakeProfileRepository().apply {
            selectRoleResult = Result.failure(java.io.IOException("no network"))
        }
        val viewModel = RoleSelectionViewModel(repository)

        viewModel.selectRole(1, onSelected = {})

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Couldn't select that role. Check your connection.", (state as UiState.Error).message)
    }
}
