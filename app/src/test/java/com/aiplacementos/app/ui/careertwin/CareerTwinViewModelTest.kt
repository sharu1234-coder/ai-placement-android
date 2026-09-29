package com.aiplacementos.app.ui.careertwin

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.CareerTwinDto
import com.aiplacementos.app.data.remote.SkillTwinItemDto
import com.aiplacementos.app.fakes.FakeCareerTwinRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal fun sampleSkillTwinItem(
    skill: String = "Python",
    weight: Float = 1.0f,
    measuredScore: Float? = null,
    confidencePercent: Int = 0,
    state: String = "claimed",
) = SkillTwinItemDto(
    skill = skill,
    weight = weight,
    measuredScore = measuredScore,
    confidencePercent = confidencePercent,
    state = state,
    lastVerifiedAt = null,
)

internal fun sampleCareerTwin(
    hasRole: Boolean = true,
    displayRoleName: String = "Software Engineer",
    roleSource: String = "career_path",
    evidenceCoveragePercent: Int? = 42,
    skills: List<SkillTwinItemDto> = listOf(sampleSkillTwinItem()),
    biggestGapSkill: String? = "Python",
    nextActionHint: String? = "Start learning Python fundamentals.",
    reasons: List<String> = listOf("Software Engineer is your current target role."),
) = CareerTwinDto(
    hasRole = hasRole,
    roleFamilyKey = "sde",
    displayRoleName = displayRoleName,
    roleSource = roleSource,
    evidenceCoveragePercent = evidenceCoveragePercent,
    evidenceCoverageTrendPercent = null,
    isEstimate = false,
    reasons = reasons,
    skills = skills,
    biggestGapSkill = biggestGapSkill,
    nextActionHint = nextActionHint,
    weakSkillTags = emptyList(),
)

class CareerTwinViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows Success when a role is set`() {
        val repository = FakeCareerTwinRepository().apply { careerTwinResult = { sampleCareerTwin() } }
        val viewModel = CareerTwinViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is CareerTwinUiState.Success)
        assertEquals("Software Engineer", (state as CareerTwinUiState.Success).twin.displayRoleName)
    }

    @Test
    fun `shows NoRole with the backend's real reason, not a fabricated one`() {
        val repository = FakeCareerTwinRepository().apply {
            careerTwinResult = {
                sampleCareerTwin(hasRole = false, evidenceCoveragePercent = null, skills = emptyList()).copy(
                    reasons = listOf("Select a target role to build your Career Twin."),
                )
            }
        }
        val viewModel = CareerTwinViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is CareerTwinUiState.NoRole)
        assertEquals("Select a target role to build your Career Twin.", (state as CareerTwinUiState.NoRole).reason)
    }

    @Test
    fun `NoRole falls back to a generic reason if the backend sends none`() {
        val repository = FakeCareerTwinRepository().apply {
            careerTwinResult = { sampleCareerTwin(hasRole = false, evidenceCoveragePercent = null, skills = emptyList()).copy(reasons = emptyList()) }
        }
        val viewModel = CareerTwinViewModel(repository)

        val state = viewModel.uiState.value as CareerTwinUiState.NoRole
        assertTrue(state.reason.isNotBlank())
    }

    @Test
    fun `surfaces an error message on failure`() {
        val repository = FakeCareerTwinRepository().apply { careerTwinResult = { throw java.io.IOException("down") } }
        val viewModel = CareerTwinViewModel(repository)

        assertTrue(viewModel.uiState.value is CareerTwinUiState.Error)
    }

    @Test
    fun `genuine connectivity failure is flagged as a network error`() {
        val repository = FakeCareerTwinRepository().apply {
            careerTwinResult = { throw UnknownHostException("no route to host") }
        }
        val viewModel = CareerTwinViewModel(repository)

        val state = viewModel.uiState.value as CareerTwinUiState.Error
        assertTrue(state.isNetworkError)
        assertTrue(state.message.contains("connection", ignoreCase = true))
    }

    @Test
    fun `timeout gets a distinct message, not the connectivity message`() {
        val repository = FakeCareerTwinRepository().apply { careerTwinResult = { throw SocketTimeoutException("timeout") } }
        val viewModel = CareerTwinViewModel(repository)

        val state = viewModel.uiState.value as CareerTwinUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("too long", ignoreCase = true))
    }

    @Test
    fun `expired session shows a sign-in message, not a connectivity message`() {
        val repository = FakeCareerTwinRepository().apply { careerTwinResult = { throw httpException(401) } }
        val viewModel = CareerTwinViewModel(repository)

        val state = viewModel.uiState.value as CareerTwinUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("session", ignoreCase = true))
    }

    @Test
    fun `server error is reported as a server problem, not a connectivity problem`() {
        val repository = FakeCareerTwinRepository().apply { careerTwinResult = { throw httpException(500) } }
        val viewModel = CareerTwinViewModel(repository)

        val state = viewModel.uiState.value as CareerTwinUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("our server", ignoreCase = true))
    }
}
