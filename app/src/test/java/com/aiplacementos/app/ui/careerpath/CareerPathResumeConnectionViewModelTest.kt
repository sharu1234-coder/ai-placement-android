package com.aiplacementos.app.ui.careerpath

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ResumeConnectionDto
import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.fakes.FakeCareerPathRepository
import com.aiplacementos.app.fakes.FakeResumeRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CareerPathResumeConnectionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load surfaces the path's resume connection plus the latest resume id`() {
        val connection = ResumeConnectionDto(2, 7, 1, 3, 28, listOf("Power BI"))
        val careerPathRepository = FakeCareerPathRepository().apply {
            pathResult = { sampleCareerPath(resumeConnection = connection) }
        }
        val resumeRepository = FakeResumeRepository().apply {
            latestResumeResult = Result.success(ResumeSummaryDto(id = 9, filename = "resume.pdf", parseStatus = "completed", createdAt = "2026-01-01T00:00:00Z"))
        }
        val viewModel = CareerPathResumeConnectionViewModel(1, careerPathRepository, resumeRepository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<*>)
        val data = (state as UiState.Success).data
        assertEquals(connection, data.path.resumeConnection)
        assertEquals(9, data.latestResumeId)
    }

    @Test
    fun `resume lookup failure never blocks the rest of the screen`() {
        val careerPathRepository = FakeCareerPathRepository().apply { pathResult = { sampleCareerPath() } }
        val resumeRepository = FakeResumeRepository().apply {
            latestResumeResult = Result.failure(java.io.IOException("down"))
        }
        val viewModel = CareerPathResumeConnectionViewModel(1, careerPathRepository, resumeRepository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<*>)
        assertNull((state as UiState.Success).data.latestResumeId)
    }
}
