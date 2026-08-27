package com.aiplacementos.app.ui.atscheck

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.fakes.FakeResumeRepository
import com.aiplacementos.app.ui.common.UiState
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AtsCheckPreCheckViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load surfaces the current resume when one exists`() {
        val resume = ResumeSummaryDto(id = 5, filename = "resume.pdf", parseStatus = "completed", createdAt = "2026-01-01T00:00:00Z")
        val repository = FakeResumeRepository().apply { latestResumeResult = Result.success(resume) }
        val viewModel = AtsCheckPreCheckViewModel(repository)

        viewModel.load()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<ResumeSummaryDto>)
        assertEquals(resume, (state as UiState.Success<ResumeSummaryDto>).data)
    }

    @Test
    fun `load surfaces Empty -- not Error -- when no resume has been uploaded yet`() {
        val repository = FakeResumeRepository().apply { latestResumeResult = Result.success(null) }
        val viewModel = AtsCheckPreCheckViewModel(repository)

        viewModel.load()

        assertTrue(viewModel.uiState.value is UiState.Empty)
    }

    @Test
    fun `load surfaces an error message on failure`() {
        val repository = FakeResumeRepository().apply { latestResumeResult = Result.failure(IOException("no network")) }
        val viewModel = AtsCheckPreCheckViewModel(repository)

        viewModel.load()

        assertTrue(viewModel.uiState.value is UiState.Error)
    }
}
