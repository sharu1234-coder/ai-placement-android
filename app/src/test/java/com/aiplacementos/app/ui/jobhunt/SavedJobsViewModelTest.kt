package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.SavedJobDto
import com.aiplacementos.app.fakes.FakeJobHuntRepository
import com.aiplacementos.app.fakes.httpException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun saved(
    id: Int = 1,
    status: String = "saved",
    notes: String? = null,
    appliedAt: String? = null,
) = SavedJobDto(
    id = id,
    status = status,
    notes = notes,
    appliedAt = appliedAt,
    createdAt = "2026-09-07T10:00:00",
    updatedAt = "2026-09-07T10:00:00",
    listing = sampleListing(),
    match = sampleMatch(),
)

class SavedJobsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the saved list`() {
        val repository = FakeJobHuntRepository().apply { listSavedResult = { listOf(saved()) } }
        val state = SavedJobsViewModel(repository).uiState.value

        assertTrue(state is SavedJobsUiState.Success)
        assertEquals(1, (state as SavedJobsUiState.Success).jobs.size)
    }

    @Test
    fun `an empty list is a real success, not an error`() {
        val repository = FakeJobHuntRepository().apply { listSavedResult = { emptyList() } }
        val state = SavedJobsViewModel(repository).uiState.value

        assertTrue(state is SavedJobsUiState.Success)
        assertTrue((state as SavedJobsUiState.Success).jobs.isEmpty())
    }

    @Test
    fun `the saved list requests no status filter so dismissed jobs remain visible there`() {
        val repository = FakeJobHuntRepository().apply { listSavedResult = { emptyList() } }
        SavedJobsViewModel(repository)

        assertNull(repository.lastSavedStatusFilter)
    }

    @Test
    fun `tracker mode excludes merely-saved jobs from the pipeline`() {
        // A job you bookmarked is not an application, and counting it as one would inflate a
        // student's sense of progress.
        val repository = FakeJobHuntRepository().apply { listSavedResult = { emptyList() } }
        val filter = com.aiplacementos.app.ui.jobhunt.ApplicationStage.entries
            .map { it.apiValue } - com.aiplacementos.app.ui.jobhunt.ApplicationStage.Saved.apiValue
        SavedJobsViewModel(repository, filter)

        assertNotNull(repository.lastSavedStatusFilter)
        assertFalse(repository.lastSavedStatusFilter!!.contains("saved"))
        assertTrue(repository.lastSavedStatusFilter!!.contains("applied"))
    }

    @Test
    fun `advancing a stage replaces just that row from the server's response`() {
        // The server owns applied_at, so the row it returns is authoritative -- recomputing it
        // client-side would invent a timestamp.
        val repository = FakeJobHuntRepository().apply {
            listSavedResult = { listOf(saved(id = 1), saved(id = 2)) }
            updateSavedResult = { id, status, _, _ ->
                saved(id = id, status = status!!, appliedAt = "2026-09-07T11:00:00")
            }
        }
        val viewModel = SavedJobsViewModel(repository)
        viewModel.updateStage(1, ApplicationStage.Applied)

        val state = viewModel.uiState.value as SavedJobsUiState.Success
        assertEquals("applied", state.jobs.first { it.id == 1 }.status)
        assertEquals("2026-09-07T11:00:00", state.jobs.first { it.id == 1 }.appliedAt)
        // The untouched row is left exactly as it was.
        assertEquals("saved", state.jobs.first { it.id == 2 }.status)
    }

    @Test
    fun `a failed stage change reports it and leaves the row unchanged`() {
        val repository = FakeJobHuntRepository().apply {
            listSavedResult = { listOf(saved()) }
            updateSavedResult = { _, _, _, _ -> throw httpException(500) }
        }
        val viewModel = SavedJobsViewModel(repository)
        viewModel.updateStage(1, ApplicationStage.Offer)

        val state = viewModel.uiState.value as SavedJobsUiState.Success
        assertEquals("saved", state.jobs.first().status)
        assertNotNull(state.actionError)
    }

    @Test
    fun `the updating flag is cleared even when the update fails`() {
        val repository = FakeJobHuntRepository().apply {
            listSavedResult = { listOf(saved()) }
            updateSavedResult = { _, _, _, _ -> throw httpException(500) }
        }
        val viewModel = SavedJobsViewModel(repository)
        viewModel.updateStage(1, ApplicationStage.Offer)

        assertTrue((viewModel.uiState.value as SavedJobsUiState.Success).updatingIds.isEmpty())
    }

    @Test
    fun `removing a job drops it from the list`() {
        val repository = FakeJobHuntRepository().apply {
            listSavedResult = { listOf(saved(id = 1), saved(id = 2)) }
        }
        val viewModel = SavedJobsViewModel(repository)
        viewModel.remove(1)

        val state = viewModel.uiState.value as SavedJobsUiState.Success
        assertEquals(listOf(2), state.jobs.map { it.id })
    }

    @Test
    fun `saving a note replaces the row`() {
        // Driven through the editor the student actually uses, rather than by calling the
        // repository method directly: before this phase nothing in the app opened that editor,
        // which is precisely why the capability existed and the feature did not.
        val repository = FakeJobHuntRepository().apply {
            listSavedResult = { listOf(saved()) }
            updateSavedResult = { id, _, notes, _ -> saved(id = id, notes = notes) }
        }
        val viewModel = SavedJobsViewModel(repository)

        viewModel.startEditingNote(1)
        viewModel.onNoteChange("Referred by Priya")
        viewModel.saveNote()

        val state = viewModel.uiState.value as SavedJobsUiState.Success
        assertEquals("Referred by Priya", state.jobs.first().notes)
        assertNull("the editor closes once the server has confirmed it", state.editingNote)
    }

    @Test
    fun `a connectivity failure is flagged so the screen can show the offline icon`() {
        val repository = FakeJobHuntRepository().apply {
            listSavedResult = { throw UnknownHostException("no route") }
        }
        val state = SavedJobsViewModel(repository).uiState.value as SavedJobsUiState.Error

        assertTrue(state.isNetworkError)
        assertTrue(state.message.contains("connection", ignoreCase = true))
    }

    @Test
    fun `the pipeline stages exclude dismissed`() {
        // "dismissed" is a valid stored status but not a step of an application.
        val labels = ApplicationStage.pipelineStages.map { it.apiValue }
        assertFalse(labels.contains("dismissed"))
        assertEquals(listOf("saved", "applied", "assessment", "interview", "offer"), labels)
    }

    @Test
    fun `every stage maps back from its wire value`() {
        // Guards against the enum drifting from the backend's SAVED_JOB_STATUSES.
        ApplicationStage.entries.forEach { stage ->
            assertEquals(stage, ApplicationStage.fromApiValue(stage.apiValue))
        }
        assertNull(ApplicationStage.fromApiValue("hired_probably"))
    }
}
