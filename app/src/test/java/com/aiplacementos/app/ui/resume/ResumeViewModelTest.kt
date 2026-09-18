package com.aiplacementos.app.ui.resume

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.fakes.FakeResumeRepository
import com.aiplacementos.app.ui.common.UiState
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun summary(id: Int = 7, parseStatus: String = "completed") = ResumeSummaryDto(
    id = id,
    filename = "asha-rao-cv.pdf",
    parseStatus = parseStatus,
    createdAt = "2026-09-12T08:30:00Z",
    fileSizeBytes = 240_000,
)

class ResumeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows the uploaded resume and fetches it for preview`() {
        val repository = FakeResumeRepository().apply {
            latestResumeResult = Result.success(summary())
            downloadResult = Result.success(byteArrayOf(1, 2, 3))
        }

        val viewModel = ResumeViewModel(repository)

        val state = viewModel.uiState.value as UiState.Success
        assertEquals("asha-rao-cv.pdf", state.data?.filename)
        assertEquals(7, repository.lastDownloadedResumeId)
        assertArrayEqualsPreview(byteArrayOf(1, 2, 3), viewModel.preview.value)
    }

    @Test
    fun `reports no resume without attempting a download`() {
        val repository = FakeResumeRepository().apply { latestResumeResult = Result.success(null) }

        val viewModel = ResumeViewModel(repository)

        assertNull((viewModel.uiState.value as UiState.Success).data)
        assertEquals(UiState.Empty, viewModel.preview.value)
        assertEquals(0, repository.downloadCount)
    }

    @Test
    fun `a failed preview does not hide the resume itself`() {
        // The whole point of loading the two separately: the file is on the server even when
        // this device cannot fetch it right now.
        val repository = FakeResumeRepository().apply {
            latestResumeResult = Result.success(summary())
            downloadResult = Result.failure(IOException("offline"))
        }

        val viewModel = ResumeViewModel(repository)

        assertTrue(viewModel.uiState.value is UiState.Success)
        val preview = viewModel.preview.value as UiState.Error
        assertTrue(preview.isNetworkError)
        assertEquals("Couldn't reach the server. Check your connection and try again.", preview.message)
    }

    @Test
    fun `classifies an expired session on load`() {
        val repository = FakeResumeRepository().apply {
            latestResumeResult = Result.failure(httpException(401))
        }

        val viewModel = ResumeViewModel(repository)

        val error = viewModel.uiState.value as UiState.Error
        assertEquals("Your session has expired. Please sign in again.", error.message)
        assertFalse(error.isNetworkError)
    }

    @Test
    fun `removing clears the preview and re-reads the server`() {
        val repository = FakeResumeRepository().apply {
            latestResumeResult = Result.success(summary())
            downloadResult = Result.success(byteArrayOf(9))
        }
        val viewModel = ResumeViewModel(repository)

        repository.latestResumeResult = Result.success(null)
        viewModel.removeResume(7)

        assertEquals(7, repository.lastDeletedResumeId)
        assertNull((viewModel.uiState.value as UiState.Success).data)
        assertEquals(UiState.Empty, viewModel.preview.value)
        assertFalse(viewModel.removing.value)
    }

    @Test
    fun `a failed removal surfaces an error instead of pretending it worked`() {
        val repository = FakeResumeRepository().apply {
            latestResumeResult = Result.success(summary())
            downloadResult = Result.success(byteArrayOf(9))
            deleteResult = Result.failure(httpException(500))
        }
        val viewModel = ResumeViewModel(repository)

        viewModel.removeResume(7)

        val error = viewModel.uiState.value as UiState.Error
        assertEquals("Something went wrong on our server. Please try again.", error.message)
        assertFalse(viewModel.removing.value)
    }

    @Test
    fun `retrying the preview re-requests the same resume`() {
        val repository = FakeResumeRepository().apply {
            latestResumeResult = Result.success(summary())
            downloadResult = Result.failure(IOException("offline"))
        }
        val viewModel = ResumeViewModel(repository)
        assertTrue(viewModel.preview.value is UiState.Error)

        repository.downloadResult = Result.success(byteArrayOf(4, 5))
        viewModel.loadPreview(7)

        assertArrayEqualsPreview(byteArrayOf(4, 5), viewModel.preview.value)
        assertEquals(2, repository.downloadCount)
    }

    @Test
    fun `status wording explains what it means for the student`() {
        assertEquals("Analysed and ready to use", parseStatusLabel("completed"))
        assertEquals("Still being analysed", parseStatusLabel("processing"))
        assertEquals("Uploaded, but we couldn't read it automatically", parseStatusLabel("failed"))
        // An unrecognised status must still say something true rather than echo a raw value.
        assertEquals("Uploaded", parseStatusLabel("something_new"))
    }

    @Test
    fun `formats only values it can actually parse`() {
        assertEquals("12 September 2026", formatUploadedDate("2026-09-12T08:30:00Z"))
        assertNull(formatUploadedDate("not-a-date"))
        assertNull(formatUploadedDate("2026-13-12T08:30:00Z"))
        assertEquals("1.0 MB", formatFileSize(1024 * 1024))
        assertEquals("234 KB", formatFileSize(240_000))
        assertEquals("512 bytes", formatFileSize(512))
    }

    private fun assertArrayEqualsPreview(expected: ByteArray, state: UiState<ByteArray>) {
        val success = state as UiState.Success
        assertTrue(expected.contentEquals(success.data))
    }

    private fun httpException(code: Int) = HttpException(
        Response.error<Unit>(code, "".toResponseBody("application/json".toMediaType())),
    )
}
