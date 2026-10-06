package com.aiplacementos.app.ui.resume

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeResumeRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/** upload()'s own HTTP-error-code branching (413/415/422/etc.) takes a real ContentResolver
 * + Uri, which this project's plain-JVM (no Robolectric, no mocking library) unit tests
 * can't drive without a real Android runtime -- a known, pre-existing gap (see the
 * production-readiness checklist's item 9 notes). reset() itself has no Android framework
 * dependency, so it's covered directly here. */
class ResumeUploadViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- operation-key identity (Phase 24-D) ---

    @Test
    fun `the same file is the same upload identity`() {
        val once = resumeUploadIdentity("resume.pdf", byteArrayOf(1, 2, 3))
        val again = resumeUploadIdentity("resume.pdf", byteArrayOf(1, 2, 3))

        assertEquals(once, again)
    }

    @Test
    fun `a different file picked after a failure is a different identity`() {
        // reset() exists so the student can pick another file after a failed upload, and that is
        // exactly what a single key slot got wrong: the new file went out under the old file's
        // key, and the server answered 409 because it fingerprints the name and the bytes.
        val first = resumeUploadIdentity("one.pdf", byteArrayOf(1, 2, 3))
        val second = resumeUploadIdentity("two.pdf", byteArrayOf(9, 9, 9))

        assertNotEquals(first, second)
    }

    @Test
    fun `the same name with different content is a different identity`() {
        assertNotEquals(
            resumeUploadIdentity("resume.pdf", byteArrayOf(1)),
            resumeUploadIdentity("resume.pdf", byteArrayOf(2)),
        )
    }

    @Test
    fun `a renamed file with identical content is a different identity, as the server sees it`() {
        // The server fingerprints filename alongside the body digest, so this really is a
        // different request and must not be answered with the first one's result.
        assertNotEquals(
            resumeUploadIdentity("resume.pdf", byteArrayOf(1)),
            resumeUploadIdentity("resume-final.pdf", byteArrayOf(1)),
        )
    }

    @Test
    fun `the identity never carries the file contents`() {
        val marker = "SECRET-RESUME-BODY".toByteArray()
        val identity = resumeUploadIdentity("resume.pdf", marker)

        assertFalse("a digest, not the document", identity.contains("SECRET-RESUME-BODY"))
    }

    @Test
    fun `starts at Empty`() {
        val viewModel = ResumeUploadViewModel(FakeResumeRepository())
        assertEquals(UiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `reset returns to Empty -- lets the screen offer Upload again after a failure`() {
        val viewModel = ResumeUploadViewModel(FakeResumeRepository())
        viewModel.reset()
        assertEquals(UiState.Empty, viewModel.uiState.value)
    }
}
