package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.SavedJobDto
import com.aiplacementos.app.fakes.FakeJobHuntRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The note a student keeps against one application.
 *
 * `SavedJob.notes` has existed end to end since Job Hunt was built -- column, schema, PATCH
 * route, repository method -- and nothing in the app ever wrote one. A student could see a
 * note on a tracker row but had no way to put one there, so the field was capability without
 * a feature.
 *
 * Most of what is checked here is about not losing what they typed. A note is written in one
 * sitting, often pasted from an email or a chat, and a save that clears the field on a dropped
 * connection costs them the whole thing.
 */
class ApplicationNotesTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun saved(id: Int = 1, notes: String? = null) = SavedJobDto(
        id = id,
        status = "applied",
        notes = notes,
        createdAt = "2026-09-25T10:00:00",
        updatedAt = "2026-09-25T10:00:00",
        listing = sampleListing(),
    )

    private fun viewModel(
        rows: List<SavedJobDto> = listOf(saved()),
        onUpdate: suspend (Int, String?, String?, String?) -> SavedJobDto = { id, _, notes, _ ->
            saved(id = id, notes = notes)
        },
    ): Pair<SavedJobsViewModel, FakeJobHuntRepository> {
        val repository = FakeJobHuntRepository().apply {
            listSavedResult = { rows }
            updateSavedResult = onUpdate
        }
        return SavedJobsViewModel(repository) to repository
    }

    private fun state(viewModel: SavedJobsViewModel) =
        viewModel.uiState.value as SavedJobsUiState.Success

    @Test
    fun `the editor is closed until the student opens it`() {
        val (viewModel, _) = viewModel()

        assertNull(state(viewModel).editingNote)
    }

    @Test
    fun `opening the editor on a job with no note starts it empty`() {
        val (viewModel, _) = viewModel()

        viewModel.startEditingNote(1)

        assertEquals("", state(viewModel).editingNote?.text)
    }

    @Test
    fun `opening the editor on a job that already has one seeds it with the existing note`() {
        val (viewModel, _) = viewModel(rows = listOf(saved(notes = "Referred by Priya")))

        viewModel.startEditingNote(1)

        assertEquals("Referred by Priya", state(viewModel).editingNote?.text)
    }

    @Test
    fun `opening the editor writes nothing`() {
        // Merely looking at an application must not touch the server. A write on open would
        // stamp updated_at on a row the student only read.
        val (viewModel, repository) = viewModel(rows = listOf(saved(notes = "Referred by Priya")))

        viewModel.startEditingNote(1)

        assertNull(repository.lastUpdatedSavedJobId)
    }

    @Test
    fun `editing a note saves what was typed`() {
        val (viewModel, repository) = viewModel()
        viewModel.startEditingNote(1)

        viewModel.onNoteChange("Recruiter call on Tuesday")
        viewModel.saveNote()

        assertEquals("Recruiter call on Tuesday", repository.lastUpdatedNotes)
        assertEquals("Recruiter call on Tuesday", state(viewModel).jobs.first().notes)
        assertNull(state(viewModel).editingNote)
    }

    @Test
    fun `surrounding whitespace is trimmed before it is stored`() {
        val (viewModel, repository) = viewModel()
        viewModel.startEditingNote(1)

        viewModel.onNoteChange("  Referred by Priya  ")
        viewModel.saveNote()

        assertEquals("Referred by Priya", repository.lastUpdatedNotes)
    }

    @Test
    fun `clearing the text clears the note on the server`() {
        // The server assigns `notes` whenever the field is present, so the empty string is how
        // a note is removed -- and a student who selected their note and deleted it means
        // there is no note now, not that it should be left as it was.
        val (viewModel, repository) = viewModel(
            rows = listOf(saved(notes = "Referred by Priya")),
            onUpdate = { id, _, notes, _ -> saved(id = id, notes = notes?.ifBlank { null }) },
        )
        viewModel.startEditingNote(1)

        viewModel.onNoteChange("")
        viewModel.saveNote()

        assertEquals("", repository.lastUpdatedNotes)
        assertNull(state(viewModel).jobs.first().notes)
    }

    @Test
    fun `a note of only spaces clears it rather than storing blanks`() {
        val (viewModel, repository) = viewModel(rows = listOf(saved(notes = "Referred by Priya")))
        viewModel.startEditingNote(1)

        viewModel.onNoteChange("     ")
        viewModel.saveNote()

        assertEquals("", repository.lastUpdatedNotes)
    }

    @Test
    fun `typing past the server's limit is capped rather than lost to a rejection`() {
        val (viewModel, repository) = viewModel()
        viewModel.startEditingNote(1)

        viewModel.onNoteChange("x".repeat(MAX_NOTE_LENGTH + 500))
        viewModel.saveNote()

        assertEquals(MAX_NOTE_LENGTH, state(viewModel).editingNote?.text?.length ?: MAX_NOTE_LENGTH)
        assertEquals(MAX_NOTE_LENGTH, repository.lastUpdatedNotes?.length)
    }

    @Test
    fun `the character counter appears only as the limit approaches`() {
        val (viewModel, _) = viewModel()
        viewModel.startEditingNote(1)

        viewModel.onNoteChange("Referred by Priya")
        assertFalse("a one-line note needs no counter", state(viewModel).editingNote!!.showCounter)

        viewModel.onNoteChange("x".repeat(MAX_NOTE_LENGTH - 10))
        assertTrue(state(viewModel).editingNote!!.showCounter)
    }

    @Test
    fun `a failed save keeps the text on screen and says why`() {
        val (viewModel, _) = viewModel(onUpdate = { _, _, _, _ -> throw IOException("offline") })
        viewModel.startEditingNote(1)
        viewModel.onNoteChange("Recruiter call on Tuesday")

        viewModel.saveNote()

        val draft = state(viewModel).editingNote
        assertNotNull("the editor stays open", draft)
        assertEquals("Recruiter call on Tuesday", draft?.text)
        assertNotNull(draft?.error)
        assertFalse(draft!!.isSaving)
    }

    @Test
    fun `a failed save does not put the note on the row`() {
        val (viewModel, _) = viewModel(onUpdate = { _, _, _, _ -> throw IOException("offline") })
        viewModel.startEditingNote(1)
        viewModel.onNoteChange("Recruiter call on Tuesday")

        viewModel.saveNote()

        assertNull("no fabricated note", state(viewModel).jobs.first().notes)
    }

    @Test
    fun `retrying after a failure succeeds without retyping`() {
        var attempts = 0
        val (viewModel, _) = viewModel(
            onUpdate = { id, _, notes, _ ->
                attempts++
                if (attempts == 1) throw IOException("offline") else saved(id = id, notes = notes)
            },
        )
        viewModel.startEditingNote(1)
        viewModel.onNoteChange("Recruiter call on Tuesday")
        viewModel.saveNote()

        viewModel.saveNote()

        assertEquals("Recruiter call on Tuesday", state(viewModel).jobs.first().notes)
        assertNull(state(viewModel).editingNote)
    }

    @Test
    fun `typing again clears the previous failure message`() {
        val (viewModel, _) = viewModel(onUpdate = { _, _, _, _ -> throw IOException("offline") })
        viewModel.startEditingNote(1)
        viewModel.onNoteChange("Recruiter call")
        viewModel.saveNote()
        assertNotNull(state(viewModel).editingNote?.error)

        viewModel.onNoteChange("Recruiter call on Tuesday")

        assertNull(state(viewModel).editingNote?.error)
    }

    @Test
    fun `five rapid taps send one write`() {
        // A note has no natural identity the server could use to recognise a duplicate, so
        // several PATCHes in flight at once would race to decide what the note says.
        val gate = CompletableDeferred<Unit>()
        val (viewModel, repository) = viewModel(
            onUpdate = { id, _, notes, _ ->
                gate.await()
                saved(id = id, notes = notes)
            },
        )
        viewModel.startEditingNote(1)
        viewModel.onNoteChange("Recruiter call on Tuesday")

        repeat(5) { viewModel.saveNote() }

        assertEquals(1, repository.updateSavedCallCount)
        assertTrue(state(viewModel).editingNote!!.isSaving)
        gate.complete(Unit)
    }

    @Test
    fun `cancelling closes the editor and leaves the note alone`() {
        val (viewModel, repository) = viewModel(rows = listOf(saved(notes = "Referred by Priya")))
        viewModel.startEditingNote(1)
        viewModel.onNoteChange("something else entirely")

        viewModel.cancelNoteEdit()

        assertNull(state(viewModel).editingNote)
        assertNull("nothing was written", repository.lastUpdatedSavedJobId)
        assertEquals("Referred by Priya", state(viewModel).jobs.first().notes)
    }

    @Test
    fun `the editor opens against the row that was tapped`() {
        val (viewModel, repository) = viewModel(
            rows = listOf(saved(id = 1, notes = "first"), saved(id = 2, notes = "second")),
        )

        viewModel.startEditingNote(2)
        viewModel.onNoteChange("updated second")
        viewModel.saveNote()

        assertEquals(2, repository.lastUpdatedSavedJobId)
        assertEquals("updated second", state(viewModel).jobs.last().notes)
        assertEquals("the other row is untouched", "first", state(viewModel).jobs.first().notes)
    }

    @Test
    fun `saving a note does not change the application stage`() {
        // The tracker's PATCH carries whichever fields are set. Sending a status alongside the
        // note would let writing "recruiter called" quietly move the application backwards.
        val (viewModel, repository) = viewModel()
        viewModel.startEditingNote(1)
        viewModel.onNoteChange("Recruiter call on Tuesday")

        viewModel.saveNote()

        assertNull(repository.lastUpdatedStatus)
    }
}
