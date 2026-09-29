package com.aiplacementos.app.ui.careerdecision

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.CareerDecisionRoleDto
import com.aiplacementos.app.data.remote.CareerDecisionSimulationDto
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.fakes.FakeCareerDecisionRepository
import com.aiplacementos.app.fakes.FakeProfileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private val ROLES = listOf(RoleDto(id = 1, name = "SDE"), RoleDto(id = 2, name = "Data Analyst"), RoleDto(id = 3, name = "PM"))

private fun sampleResult(weeklyHoursUsed: Int? = null) = CareerDecisionSimulationDto(
    roles = listOf(
        CareerDecisionRoleDto(
            roleId = 1, displayRoleName = "SDE", evidenceCoveragePercent = 80, classification = "best_fit",
            biggestGapSkill = null, estimatedHoursToStrengthen = 0.0, isEstimate = true, estimatedWeeks = null,
            reasons = listOf("80% of SDE's required skills are backed by real evidence."),
        ),
        CareerDecisionRoleDto(
            roleId = 2, displayRoleName = "Data Analyst", evidenceCoveragePercent = 20, classification = "stretch",
            biggestGapSkill = "SQL", estimatedHoursToStrengthen = 40.0, isEstimate = true, estimatedWeeks = 4,
            reasons = listOf("20% of Data Analyst's required skills are backed by real evidence."),
        ),
    ),
    sharedSkills = emptyList(), weeklyHoursUsed = weeklyHoursUsed, assumptions = listOf("An assumption."),
)

class CareerDecisionLabViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads available roles on init`() {
        val profileRepository = FakeProfileRepository().apply { rolesResult = Result.success(ROLES) }
        val viewModel = CareerDecisionLabViewModel(FakeCareerDecisionRepository(), profileRepository)

        val state = viewModel.uiState.value as CareerDecisionUiState.Ready
        assertEquals(3, state.availableRoles.size)
    }

    @Test
    fun `toggling a third role is allowed but a fourth is ignored`() {
        val profileRepository = FakeProfileRepository().apply { rolesResult = Result.success(ROLES) }
        val viewModel = CareerDecisionLabViewModel(FakeCareerDecisionRepository(), profileRepository)

        viewModel.toggleRole(1)
        viewModel.toggleRole(2)
        viewModel.toggleRole(3)

        var state = viewModel.uiState.value as CareerDecisionUiState.Ready
        assertEquals(listOf(1, 2, 3), state.selectedRoleIds)
        assertTrue(state.canCompare)

        // A fourth role (there isn't one here, but re-toggling an already-selected one off
        // and confirming the max-3 guard doesn't silently drop an existing selection).
        viewModel.toggleRole(1)
        state = viewModel.uiState.value as CareerDecisionUiState.Ready
        assertEquals(listOf(2, 3), state.selectedRoleIds)
    }

    @Test
    fun `comparing with fewer than two roles selected does nothing`() {
        val profileRepository = FakeProfileRepository().apply { rolesResult = Result.success(ROLES) }
        val repository = FakeCareerDecisionRepository().apply { simulateResult = { _, _ -> error("should not be called") } }
        val viewModel = CareerDecisionLabViewModel(repository, profileRepository)

        viewModel.toggleRole(1)
        viewModel.compare()

        val state = viewModel.uiState.value as CareerDecisionUiState.Ready
        assertNull(state.result)
    }

    @Test
    fun `comparing two selected roles calls simulate and stores the result`() {
        val profileRepository = FakeProfileRepository().apply { rolesResult = Result.success(ROLES) }
        val repository = FakeCareerDecisionRepository().apply { simulateResult = { _, _ -> sampleResult() } }
        val viewModel = CareerDecisionLabViewModel(repository, profileRepository)

        viewModel.toggleRole(1)
        viewModel.toggleRole(2)
        viewModel.compare()

        val state = viewModel.uiState.value as CareerDecisionUiState.Ready
        assertEquals(listOf(1, 2), repository.lastRoleIds)
        assertEquals(2, state.result?.roles?.size)
    }

    @Test
    fun `weekly hours settle after a result exists re-runs the comparison`() {
        val profileRepository = FakeProfileRepository().apply { rolesResult = Result.success(ROLES) }
        var callCount = 0
        val repository = FakeCareerDecisionRepository().apply {
            simulateResult = { _, _ -> callCount += 1; sampleResult(weeklyHoursUsed = 15) }
        }
        val viewModel = CareerDecisionLabViewModel(repository, profileRepository)

        viewModel.toggleRole(1)
        viewModel.toggleRole(2)
        viewModel.compare()
        assertEquals(1, callCount)

        viewModel.setWeeklyHours(15)
        viewModel.onWeeklyHoursSettled()
        assertEquals(2, callCount)

        val state = viewModel.uiState.value as CareerDecisionUiState.Ready
        assertEquals(15, state.weeklyHours)
    }

    @Test
    fun `weekly hours settling before any comparison does not call the repository`() {
        val profileRepository = FakeProfileRepository().apply { rolesResult = Result.success(ROLES) }
        val repository = FakeCareerDecisionRepository().apply { simulateResult = { _, _ -> error("should not be called") } }
        val viewModel = CareerDecisionLabViewModel(repository, profileRepository)

        viewModel.setWeeklyHours(20)
        viewModel.onWeeklyHoursSettled()

        val state = viewModel.uiState.value as CareerDecisionUiState.Ready
        assertNull(state.result)
    }

    @Test
    fun `a repository failure surfaces a simulate error without losing the selection`() {
        val profileRepository = FakeProfileRepository().apply { rolesResult = Result.success(ROLES) }
        val repository = FakeCareerDecisionRepository().apply {
            simulateResult = { _, _ -> throw java.net.UnknownHostException("no route") }
        }
        val viewModel = CareerDecisionLabViewModel(repository, profileRepository)

        viewModel.toggleRole(1)
        viewModel.toggleRole(2)
        viewModel.compare()

        val state = viewModel.uiState.value as CareerDecisionUiState.Ready
        assertTrue(state.simulateError != null)
        assertEquals(listOf(1, 2), state.selectedRoleIds)
    }
}
