package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.SavedJobDto
import com.aiplacementos.app.fakes.FakeJobHuntRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun savedJob(id: Int = 7, status: String = "saved") = SavedJobDto(
    id = id,
    status = status,
    createdAt = "2026-09-07T10:00:00",
    updatedAt = "2026-09-07T10:00:00",
    listing = sampleListing(),
)

class JobDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the job with this student's own match`() {
        val repository = FakeJobHuntRepository().apply { getJobResult = { sampleResult(id = it) } }
        val state = JobDetailViewModel(repository, listingId = 3).uiState.value

        assertTrue(state is JobDetailUiState.Success)
        state as JobDetailUiState.Success
        assertEquals(3, state.job.listing.id)
        assertTrue(state.job.match.reasons.isNotEmpty())
    }

    @Test
    fun `a missing listing is a failure, not an empty screen`() {
        val repository = FakeJobHuntRepository().apply {
            getJobResult = { throw httpException(404) }
        }
        val state = JobDetailViewModel(repository, listingId = 3).uiState.value
        assertTrue(state is JobDetailUiState.Failed)
    }

    @Test
    fun `an unconfigured provider is not offered a pointless retry`() {
        val repository = FakeJobHuntRepository().apply {
            getJobResult = { throw httpException(503) }
        }
        val state = JobDetailViewModel(repository, listingId = 3).uiState.value as JobDetailUiState.Failed
        assertFalse(state.failure.isRetryable)
    }

    @Test
    fun `an existing saved state arrives with the job`() {
        val repository = FakeJobHuntRepository().apply {
            getJobResult = { sampleResult(id = it, savedJobId = 7) }
        }
        val state = JobDetailViewModel(repository, listingId = 3).uiState.value as JobDetailUiState.Success
        assertEquals(7, state.savedJobId)
    }

    @Test
    fun `saving and unsaving toggles cleanly`() {
        val repository = FakeJobHuntRepository().apply {
            getJobResult = { sampleResult(id = it) }
            saveJobResult = { _, _ -> savedJob(id = 7) }
        }
        val viewModel = JobDetailViewModel(repository, listingId = 3)

        viewModel.toggleSave()
        assertEquals(7, (viewModel.uiState.value as JobDetailUiState.Success).savedJobId)

        viewModel.toggleSave()
        assertNull((viewModel.uiState.value as JobDetailUiState.Success).savedJobId)
        assertEquals(listOf(7), repository.deletedSavedIds)
    }

    @Test
    fun `a failed save reports it without losing the loaded job`() {
        val repository = FakeJobHuntRepository().apply {
            getJobResult = { sampleResult(id = it) }
            saveJobResult = { _, _ -> throw httpException(500) }
        }
        val viewModel = JobDetailViewModel(repository, listingId = 3)
        viewModel.toggleSave()

        val state = viewModel.uiState.value as JobDetailUiState.Success
        assertNotNull(state.actionError)
        assertNull(state.savedJobId)
        assertEquals(3, state.job.listing.id)
        assertFalse(state.isSaving)
    }

    @Test
    fun `applying saves the job first when it was not already saved`() {
        // The student took a real action -- opening the employer's listing -- so there has to
        // be a row to attach the status to.
        val repository = FakeJobHuntRepository().apply {
            getJobResult = { sampleResult(id = it) }
            saveJobResult = { _, _ -> savedJob(id = 7) }
            updateSavedResult = { id, status, _, _ -> savedJob(id = id, status = status!!) }
        }
        val viewModel = JobDetailViewModel(repository, listingId = 3)
        viewModel.markApplied()

        assertEquals(listOf(3), repository.savedListingIds)
        assertEquals(7, repository.lastUpdatedSavedJobId)
        assertEquals("applied", repository.lastUpdatedStatus)
    }

    @Test
    fun `applying an already-saved job reuses the existing row rather than duplicating it`() {
        val repository = FakeJobHuntRepository().apply {
            getJobResult = { sampleResult(id = it, savedJobId = 7) }
            updateSavedResult = { id, status, _, _ -> savedJob(id = id, status = status!!) }
        }
        val viewModel = JobDetailViewModel(repository, listingId = 3)
        viewModel.markApplied()

        assertTrue(repository.savedListingIds.isEmpty())
        assertEquals(7, repository.lastUpdatedSavedJobId)
    }

    @Test
    fun `a device with no browser is told, and the job is not marked applied`() {
        // The Apply button used to swallow ActivityNotFoundException entirely: the tap did
        // nothing and said nothing, which reads as a frozen screen. Recording the application
        // anyway would be the other, worse fix -- nothing was opened, so nothing was applied
        // to.
        val repository = FakeJobHuntRepository().apply { getJobResult = { sampleResult(id = it) } }
        val vm = JobDetailViewModel(repository, listingId = 3)

        vm.reportNoBrowser()

        val state = vm.uiState.value as JobDetailUiState.Success
        assertNotNull(state.actionError)
        assertNull("no application may be recorded", repository.lastUpdatedStatus)
    }

    @Test
    fun `the message clears so it does not reappear on the next recomposition`() {
        val repository = FakeJobHuntRepository().apply { getJobResult = { sampleResult(id = it) } }
        val vm = JobDetailViewModel(repository, listingId = 3)
        vm.reportNoBrowser()

        vm.clearActionError()

        assertNull((vm.uiState.value as JobDetailUiState.Success).actionError)
    }
}
