package com.aiplacementos.app.ui.careerpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.JobReadinessDto
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CareerPathReadinessViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load surfaces the path's job readiness`() {
        val repository = FakeCareerPathRepository().apply { pathResult = { sampleCareerPath() } }
        val viewModel = CareerPathReadinessViewModel(1, repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<*>)
        assertEquals(42, (state as UiState.Success).data.jobReadiness.overallPercent)
    }

    @Test
    fun `the denominator survives the trip from the wire into screen state`() {
        val repository = FakeCareerPathRepository().apply {
            pathResult = {
                sampleCareerPath(
                    jobReadiness = JobReadinessDto(
                        overallPercent = 78,
                        contributingSignals = 2,
                        totalSignals = 5,
                        skillsPercent = 80,
                        projectsPercent = 76,
                        resumePercent = null,
                        practicePercent = null,
                        interviewPercent = null,
                        topFocusAreas = listOf("Strengthen SQL"),
                    ),
                )
            }
        }
        val viewModel = CareerPathReadinessViewModel(1, repository)

        val readiness = (viewModel.uiState.value as UiState.Success).data.jobReadiness
        assertEquals(2, readiness.contributingSignals)
        assertEquals(5, readiness.totalSignals)
        // 78% averaged over two of five signals is the case this whole finding is about.
        assertEquals(
            "Measured from 2 of 5 signals",
            readinessCoverageLabel(readiness.contributingSignals, readiness.totalSignals),
        )
    }

    @Test
    fun `load surfaces an error message on failure`() {
        val repository = FakeCareerPathRepository().apply { pathResult = { throw java.io.IOException("down") } }
        val viewModel = CareerPathReadinessViewModel(1, repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }
}
