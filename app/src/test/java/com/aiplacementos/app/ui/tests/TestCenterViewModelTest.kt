package com.aiplacementos.app.ui.tests

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.AssessmentBlueprintDto
import com.aiplacementos.app.data.remote.AssessmentHistoryItemDto
import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.fakes.FakeAssessmentRepository
import com.aiplacementos.app.fakes.FakeProfileRepository
import com.aiplacementos.app.fakes.FakeProgressRepository
import com.aiplacementos.app.fakes.httpException
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TestCenterViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        history: List<AssessmentHistoryItemDto> = emptyList(),
        weakAreas: List<String> = emptyList(),
        targetRoleId: Int? = 5,
    ): Triple<TestCenterViewModel, FakeAssessmentRepository, FakeProfileRepository> {
        val assessmentRepository = FakeAssessmentRepository().apply { historyResult = { history } }
        val progressRepository = FakeProgressRepository().apply {
            dashboardResult = Result.success(dashboardResult.getOrThrow().copy(topWeaknesses = weakAreas))
        }
        val profileRepository = FakeProfileRepository().apply {
            profileResult = Result.success(profileResult.getOrThrow().copy(targetRoleId = targetRoleId))
        }
        return Triple(
            TestCenterViewModel(assessmentRepository, progressRepository, profileRepository),
            assessmentRepository,
            profileRepository,
        )
    }

    @Test
    fun `computes average score and accuracy as percentages across completed tests`() {
        val history = listOf(
            historyItem(overallScore = 1.0, accuracy = 0.8),
            historyItem(overallScore = 0.5, accuracy = 0.6),
        )
        val (viewModel, _, _) = viewModel(history = history)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<TestCenterData>)
        val data = (state as UiState.Success<TestCenterData>).data
        assertEquals(75, data.averageScorePercent)
        assertEquals(70, data.accuracyPercent)
        assertEquals(2, data.testsCompleted)
    }

    @Test
    fun `no completed tests yields null averages instead of a fake zero`() {
        val (viewModel, _, _) = viewModel(history = emptyList())

        val data = (viewModel.uiState.value as UiState.Success<TestCenterData>).data
        assertNull(data.averageScorePercent)
        assertNull(data.accuracyPercent)
        assertEquals(0, data.testsCompleted)
    }

    @Test
    fun `weak areas come from the progress dashboard`() {
        val (viewModel, _, _) = viewModel(weakAreas = listOf("SQL JOINs", "Dynamic Programming"))

        val data = (viewModel.uiState.value as UiState.Success<TestCenterData>).data
        assertEquals(listOf("SQL JOINs", "Dynamic Programming"), data.weakAreas)
    }

    @Test
    fun `startCategory creates a blueprint scoped to the student's target role and category`() {
        val (viewModel, assessmentRepository, _) = viewModel(targetRoleId = 9)
        assessmentRepository.createBlueprintResult = { _, _, _ -> AssessmentBlueprintDto(id = 55, roleId = 9, jdId = null, source = "default_taxonomy", sections = emptyList()) }

        var readyBlueprintId: Int? = null
        viewModel.startCategory(TestCategory("SQL", "sql"), onBlueprintReady = { readyBlueprintId = it })

        assertEquals(Triple(9, null, "sql"), assessmentRepository.lastCreateBlueprintArgs)
        assertEquals(55, readyBlueprintId)
        assertNull(viewModel.startError.value)
    }

    @Test
    fun `startCategory without a target role surfaces a guidance message instead of calling the API`() {
        val (viewModel, assessmentRepository, _) = viewModel(targetRoleId = null)

        viewModel.startCategory(TestCategory("SQL", "sql"), onBlueprintReady = {})

        assertEquals("Set a target role in your profile to start a test.", viewModel.startError.value)
        assertNull(assessmentRepository.lastCreateBlueprintArgs)
    }

    @Test
    fun `startCategory surfaces a not-enough-content message on 422`() {
        val (viewModel, assessmentRepository, _) = viewModel(targetRoleId = 9)
        assessmentRepository.createBlueprintResult = { _, _, _ -> throw httpException(422) }

        viewModel.startCategory(TestCategory("SQL", "sql"), onBlueprintReady = {})

        assertEquals("Not enough SQL content for your role yet -- try another category.", viewModel.startError.value)
    }
}

private fun historyItem(overallScore: Double, accuracy: Double) = AssessmentHistoryItemDto(
    assessmentId = 1,
    blueprintId = 1,
    category = "coding",
    overallScore = overallScore,
    accuracy = accuracy,
    questionCount = 6,
    completedAt = "2026-01-01T00:00:00Z",
)
