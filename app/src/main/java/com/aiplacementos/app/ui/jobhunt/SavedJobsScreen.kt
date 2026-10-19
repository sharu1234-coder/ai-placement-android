package com.aiplacementos.app.ui.jobhunt

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.SavedJobDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.AppFab
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

/**
 * Saved jobs and the application tracker are one screen with two modes rather than two screens:
 * the content, row layout and actions are identical, and the only difference is which statuses
 * are listed. [trackerMode] excludes dismissed and merely-saved jobs, so the pipeline reflects
 * applications a student actually started.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedJobsScreen(
    onBack: () -> Unit,
    onOpenJob: (Int) -> Unit,
    /** Opens the manual-entry form. This screen is where the CTA belongs: a student who found
     * a job elsewhere is looking for the place their applications live, not for a search. */
    onAddManualJob: () -> Unit = {},
    trackerMode: Boolean = false,
) {
    val network = networkModule()
    val statusFilter = if (trackerMode) {
        ApplicationStage.entries.map { it.apiValue } - ApplicationStage.Saved.apiValue
    } else {
        null
    }
    val viewModel: SavedJobsViewModel = viewModel(
        key = if (trackerMode) "tracker" else "saved",
        factory = viewModelFactory {
            initializer { SavedJobsViewModel(network.jobHuntRepository, statusFilter) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    val actionError = (state as? SavedJobsUiState.Success)?.actionError
    LaunchedEffect(actionError) {
        if (actionError != null) {
            snackbarHostState.showSnackbar(actionError)
            viewModel.clearActionError()
        }
    }

    Scaffold(
        topBar = {
            DetailTopBar(
                title = if (trackerMode) "My Applications" else "Saved Jobs",
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { AppFab(onClick = onAddManualJob, contentDescription = "Add a job") },
    ) { padding ->
        when (val current = state) {
            is SavedJobsUiState.Loading ->
                SkeletonList(count = 4, modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md))

            is SavedJobsUiState.Error -> ErrorState(
                message = current.message,
                icon = if (current.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
                onRetry = viewModel::load,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            is SavedJobsUiState.Success -> if (current.jobs.isEmpty()) {
                // The empty state names both ways in, not just the search. Before manual entry
                // existed, a student with no provider configured -- or one out of free
                // searches -- read "save a job from your matches" as an instruction they had
                // no way to follow.
                EmptyState(
                    title = if (trackerMode) "No applications yet" else "No saved jobs yet",
                    message = if (trackerMode) {
                        "Apply to a job from your matches, or add one you found elsewhere, " +
                            "and track it here."
                    } else {
                        "Save a job from your matches, or add one you found elsewhere."
                    },
                    icon = Icons.Filled.BookmarkBorder,
                    actionLabel = "Add a job",
                    onAction = onAddManualJob,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            } else {
                SavedJobsList(
                    state = current,
                    onOpenJob = onOpenJob,
                    onStageChange = viewModel::updateStage,
                    onEditNote = viewModel::startEditingNote,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    // Hosted by the screen rather than by a row: a dialog owned by a LazyColumn item is
    // disposed the moment that row scrolls out of view, which would close the editor and
    // discard whatever had been typed into it.
    (state as? SavedJobsUiState.Success)?.editingNote?.let { draft ->
        NoteEditorDialog(
            draft = draft,
            jobTitle = noteEditorTitle(state, draft.savedJobId),
            onTextChange = viewModel::onNoteChange,
            onSave = viewModel::saveNote,
            onDismiss = viewModel::cancelNoteEdit,
        )
    }
}

/** The title of the job a draft belongs to, so the dialog names what is being annotated. */
private fun noteEditorTitle(state: SavedJobsUiState, savedJobId: Int): String =
    (state as? SavedJobsUiState.Success)
        ?.jobs?.find { it.id == savedJobId }?.listing?.title
        ?: "this application"

/**
 * Writing the note for one tracked application.
 *
 * A dialog rather than an inline field, for two reasons. A tracker row is already dense --
 * title, company, provenance, stage chips, match -- and an always-open multi-line field on
 * every row would bury the stage controls that are the screen's actual job. And a dialog gives
 * the save action somewhere unambiguous to live, which an inline field has to invent.
 */
@Composable
internal fun NoteEditorDialog(
    draft: NoteDraft,
    jobTitle: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        // Dismissing while a write is in flight would leave the student unsure whether it
        // landed, so the dialog holds until the server answers.
        onDismissRequest = { if (!draft.isSaving) onDismiss() },
        title = { Text("Note for " + jobTitle) },
        text = {
            OutlinedTextField(
                value = draft.text,
                onValueChange = onTextChange,
                // A real label, not a placeholder standing in for one: a placeholder
                // disappears as soon as there is text, taking the field's name with it.
                label = { Text("Your note") },
                placeholder = { Text("e.g. Referred by Priya, follow up Monday") },
                minLines = 3,
                maxLines = 8,
                enabled = !draft.isSaving,
                isError = draft.error != null,
                supportingText = {
                    when {
                        draft.error != null -> Text(draft.error)
                        draft.showCounter ->
                            Text(draft.text.length.toString() + " of " + MAX_NOTE_LENGTH + " characters")
                        else -> Text("Only you can see this.")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            PrimaryButton(
                text = "Save note",
                loading = draft.isSaving,
                onClick = onSave,
                enabled = !draft.isSaving,
            )
        },
        dismissButton = {
            SecondaryButton(text = "Cancel", onClick = onDismiss, enabled = !draft.isSaving)
        },
    )
}

@Composable
private fun SavedJobsList(
    state: SavedJobsUiState.Success,
    onOpenJob: (Int) -> Unit,
    onStageChange: (Int, ApplicationStage) -> Unit,
    onEditNote: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(state.jobs, key = { it.id }) { saved ->
            SavedJobRow(
                saved = saved,
                isUpdating = saved.id in state.updatingIds,
                onOpen = { onOpenJob(saved.listing.id) },
                onStageChange = { stage -> onStageChange(saved.id, stage) },
                onEditNote = { onEditNote(saved.id) },
            )
        }
    }
}

@Composable
private fun SavedJobRow(
    saved: SavedJobDto,
    isUpdating: Boolean,
    onOpen: () -> Unit,
    onStageChange: (ApplicationStage) -> Unit,
    onEditNote: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = saved.listing.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            val subtitle = listOfNotNull(saved.listing.company, saved.listing.location)
                .joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Where this job came from, and when it was applied to. Both are facts recorded
            // at the time; neither is recomputed, so the row keeps telling the truth about
            // its own history after the student changes its stage.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = savedJobSourceLabel(saved.source, saved.sourceSavedSearchName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                appliedOnLabel(saved.appliedAt)?.let { applied ->
                    Text(
                        text = applied,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Only when the server actually computed one, and versioned, so a student can see
            // which engine produced the number sitting in their tracker.
            saved.match?.let { match ->
                Text(
                    text = "Match ${match.matchPercent()}% (${match.scoreVersion})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // The stage row scrolls horizontally rather than wrapping: at a large font scale
            // five chips will not fit on one line on a small phone, and a clipped final stage
            // would be unreachable.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
            ) {
                ApplicationStage.pipelineStages.forEach { stage ->
                    FilterChip(
                        selected = saved.status == stage.apiValue,
                        onClick = { onStageChange(stage) },
                        enabled = !isUpdating,
                        label = { Text(stage.label) },
                    )
                }
            }

            val current = ApplicationStage.fromApiValue(saved.status)
            if (current == ApplicationStage.Rejected || current == ApplicationStage.Closed) {
                Text(
                    text = current.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }

            // The note, and the way in to writing one. Until this phase the text was
            // rendered but no control anywhere in the app could produce it, so a row could
            // only ever show a note put there by something other than this app.
            if (!saved.notes.isNullOrBlank()) {
                Text(
                    text = saved.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            TextButton(onClick = onEditNote, modifier = Modifier.padding(top = Spacing.xs)) {
                Icon(
                    imageVector = Icons.Filled.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    // Names which of the two things the tap does, so a screen reader announces
                    // "Add a note" on a row with none and "Edit note" on a row that has one.
                    text = if (saved.notes.isNullOrBlank()) "Add a note" else "Edit note",
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }

            // Only rendered from a real stored match. A saved job with no computed match shows
            // nothing here rather than a zero.
            val match = saved.match
            if (match != null) {
                Text(
                    text = "${match.matchPercent()}% match",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }

            SecondaryButton(
                text = "View job",
                onClick = onOpen,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}
