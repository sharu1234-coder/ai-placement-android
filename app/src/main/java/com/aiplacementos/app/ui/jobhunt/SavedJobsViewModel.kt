package com.aiplacementos.app.ui.jobhunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.SavedJobDto
import com.aiplacementos.app.domain.repository.JobHuntRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The application pipeline, in the order the tracker renders its columns. Mirrors the
 * backend's SAVED_JOB_STATUSES exactly -- a value the server would reject must never be
 * offerable in the UI.
 *
 * "dismissed" is deliberately absent from [pipelineStages]: a dismissed job is not a stage of
 * an application, and showing it as one would inflate a student's sense of progress.
 */
enum class ApplicationStage(val apiValue: String, val label: String) {
    Saved("saved", "Saved"),
    Applied("applied", "Applied"),
    Assessment("assessment", "Assessment"),
    Interview("interview", "Interview"),
    Offer("offer", "Offer"),
    Rejected("rejected", "Rejected"),
    Closed("closed", "Closed"),
    ;

    companion object {
        fun fromApiValue(value: String): ApplicationStage? = entries.find { it.apiValue == value }

        val pipelineStages: List<ApplicationStage>
            get() = listOf(Saved, Applied, Assessment, Interview, Offer)
    }
}

/**
 * The most characters the server will store in a note.
 *
 * Mirrors UpdateSavedJobRequest.notes' max_length exactly (app/schemas/job_hunt.py). The
 * column itself is TEXT and unbounded, so this schema limit is the only one -- and the editor
 * enforces it rather than letting a student type six thousand characters and lose them to a
 * 422 they could not have predicted.
 */
const val MAX_NOTE_LENGTH = 5000

/**
 * One note being written, held apart from the row it belongs to.
 *
 * The draft lives here rather than in the composable because the failure path is the whole
 * point: a refused save must leave the student's text exactly where it was, and state a
 * LazyColumn row owns is state that disappears when the row scrolls out of view.
 */
data class NoteDraft(
    val savedJobId: Int,
    val text: String,
    val error: String? = null,
    val isSaving: Boolean = false,
) {
    /** Shown only as the limit approaches -- a counter on every note would be noise on the
     * one-line notes that are most of them. */
    val showCounter: Boolean get() = text.length > MAX_NOTE_LENGTH - 500
}

sealed interface SavedJobsUiState {
    data object Loading : SavedJobsUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : SavedJobsUiState
    data class Success(
        val jobs: List<SavedJobDto>,
        val updatingIds: Set<Int> = emptySet(),
        val actionError: String? = null,
        /** Non-null while the note editor is open for one tracked job. */
        val editingNote: NoteDraft? = null,
    ) : SavedJobsUiState
}

class SavedJobsViewModel(
    private val jobHuntRepository: JobHuntRepository,
    /** When non-null, only these statuses are shown -- used by the tracker to exclude
     * dismissed jobs, while the Saved list shows everything. */
    private val statusFilter: List<String>? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SavedJobsUiState>(SavedJobsUiState.Loading)
    val uiState: StateFlow<SavedJobsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SavedJobsUiState.Loading
            try {
                _uiState.value = SavedJobsUiState.Success(jobHuntRepository.listSaved(statusFilter))
            } catch (e: Exception) {
                _uiState.value = SavedJobsUiState.Error(
                    e.toUserMessage("load your saved jobs"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }

    fun updateStage(savedJobId: Int, stage: ApplicationStage) {
        val current = _uiState.value as? SavedJobsUiState.Success ?: return
        if (savedJobId in current.updatingIds) return

        viewModelScope.launch {
            _uiState.value = current.copy(
                updatingIds = current.updatingIds + savedJobId,
                actionError = null,
            )
            try {
                val updated = jobHuntRepository.updateSaved(savedJobId, status = stage.apiValue)
                // Replace the single row from the server's response rather than reloading the
                // whole list: the server owns applied_at, and re-fetching one row keeps the
                // student's scroll position.
                updateSuccess { state ->
                    state.copy(jobs = state.jobs.map { if (it.id == savedJobId) updated else it })
                }
            } catch (e: Exception) {
                updateSuccess { it.copy(actionError = e.toUserMessage("update this application")) }
            } finally {
                updateSuccess { it.copy(updatingIds = it.updatingIds - savedJobId) }
            }
        }
    }

    /**
     * Open the note editor for one tracked job, seeded with whatever is already stored.
     *
     * Opening it is a purely local act: nothing is fetched and nothing is written. The note is
     * already on the row that was loaded with the list, so reading it costs no request, and
     * writing on open would save a note the student never typed.
     */
    fun startEditingNote(savedJobId: Int) {
        val current = _uiState.value as? SavedJobsUiState.Success ?: return
        val job = current.jobs.find { it.id == savedJobId } ?: return
        _uiState.value = current.copy(
            editingNote = NoteDraft(savedJobId = savedJobId, text = job.notes.orEmpty()),
        )
    }

    /**
     * Typing. Capped at what the server will accept rather than letting the student write past
     * the limit and lose it to a 422 -- and the error clears as they type, so a message about
     * the previous attempt does not sit over the new text.
     */
    fun onNoteChange(text: String) {
        val current = _uiState.value as? SavedJobsUiState.Success ?: return
        val draft = current.editingNote ?: return
        _uiState.value = current.copy(
            editingNote = draft.copy(text = text.take(MAX_NOTE_LENGTH), error = null),
        )
    }

    fun cancelNoteEdit() {
        val current = _uiState.value as? SavedJobsUiState.Success ?: return
        _uiState.value = current.copy(editingNote = null)
    }

    /**
     * Write the open draft.
     *
     * Trimmed, and an all-whitespace note is sent as the empty string. That is not a semantic
     * this client invents: update_saved_job assigns `notes` whenever it is not None, so ""
     * clears the note and a missing field leaves it alone -- and a student who selected their
     * note and deleted it means "there is no note now", not "store three spaces".
     *
     * One write per tap. The in-flight flag is what stands between an impatient student and
     * five identical PATCHes racing each other to decide what the note says.
     */
    fun saveNote() {
        val current = _uiState.value as? SavedJobsUiState.Success ?: return
        val draft = current.editingNote ?: return
        if (draft.isSaving) return

        val note = draft.text.trim()
        viewModelScope.launch {
            _uiState.value = current.copy(editingNote = draft.copy(isSaving = true, error = null))
            try {
                val updated = jobHuntRepository.updateSaved(draft.savedJobId, notes = note)
                updateSuccess { state ->
                    state.copy(
                        jobs = state.jobs.map { if (it.id == draft.savedJobId) updated else it },
                        // Closed only once the server has confirmed it. Closing on the tap
                        // would show the row's old note back as though nothing had happened.
                        editingNote = null,
                    )
                }
            } catch (e: Exception) {
                updateSuccess { state ->
                    // The editor stays open with every character still in it. Retrying is one
                    // more tap, not retyping the note.
                    state.copy(
                        editingNote = state.editingNote?.copy(
                            isSaving = false,
                            error = e.toUserMessage("save your note"),
                        ),
                    )
                }
            }
        }
    }

    fun remove(savedJobId: Int) {
        viewModelScope.launch {
            try {
                jobHuntRepository.deleteSaved(savedJobId)
                updateSuccess { state ->
                    state.copy(jobs = state.jobs.filterNot { it.id == savedJobId })
                }
            } catch (e: Exception) {
                updateSuccess { it.copy(actionError = e.toUserMessage("remove this job")) }
            }
        }
    }

    fun clearActionError() = updateSuccess { it.copy(actionError = null) }

    private fun updateSuccess(transform: (SavedJobsUiState.Success) -> SavedJobsUiState.Success) {
        val latest = _uiState.value as? SavedJobsUiState.Success ?: return
        _uiState.value = transform(latest)
    }
}

/**
 * The provenance line on a tracker row: where this job came from.
 *
 * Recorded when the job was saved and never recomputed, so it stays true after the student
 * changes its status — a job found through a saved search was still found that way once it
 * reaches the interview stage.
 *
 * When the naming saved search has since been deleted, this falls back to the generic label
 * rather than claiming the job was saved manually. The row keeps its history; it just can no
 * longer name the search.
 */
fun savedJobSourceLabel(source: String, savedSearchName: String?): String = when (source) {
    "saved_search" ->
        if (savedSearchName.isNullOrBlank()) "Saved search" else "Saved search: $savedSearchName"
    "job_alert" -> "Job alert"
    "recommended" -> "Recommended for you"
    "manual" -> "Manually saved"
    // An unrecognised source from a newer backend falls back to the safest true statement
    // rather than to a raw enum or to a specific claim we cannot support.
    else -> "Saved"
}

/**
 * "Applied on 5 Sep 2026", or null when it has not been applied to.
 *
 * Only ever derived from the server's own applied_at. Opening an external apply link does not
 * set it — the app cannot see whether a student finished an application on someone else's
 * site, and claiming otherwise would put a false fact in their own tracker.
 */
fun appliedOnLabel(appliedAt: String?): String? {
    if (appliedAt.isNullOrBlank()) return null
    val date = appliedAt.substringBefore('T')
    val parts = date.split("-")
    if (parts.size != 3) return null
    val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
    val month = parts[1].toIntOrNull()?.let { months.getOrNull(it - 1) } ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return "Applied on $day $month ${parts[0]}"
}
