package com.aiplacementos.app.ui.jobhunt

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.SavedJobDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
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
                EmptyState(
                    title = if (trackerMode) "No applications yet" else "No saved jobs yet",
                    message = if (trackerMode) {
                        "When you apply to a job, it'll show up here so you can track it."
                    } else {
                        "Save a job from your matches and it'll appear here."
                    },
                    icon = Icons.Filled.BookmarkBorder,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            } else {
                SavedJobsList(
                    state = current,
                    onOpenJob = onOpenJob,
                    onStageChange = viewModel::updateStage,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun SavedJobsList(
    state: SavedJobsUiState.Success,
    onOpenJob: (Int) -> Unit,
    onStageChange: (Int, ApplicationStage) -> Unit,
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

            if (!saved.notes.isNullOrBlank()) {
                Text(
                    text = saved.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
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
