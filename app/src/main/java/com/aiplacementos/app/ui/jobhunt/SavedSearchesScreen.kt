package com.aiplacementos.app.ui.jobhunt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.SavedSearchDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

/**
 * Saved searches — a named set of filters the student can re-run.
 *
 * Re-running hands the saved filters straight back to the results screen rather than
 * reproducing the search here, so there is exactly one path that turns filters into jobs and
 * exactly one place that classifies its failures.
 *
 * Nothing on a row is invented. A search that has never run shows no result count at all,
 * because rendering "0 jobs" for a search we never performed would tell a student the market
 * is empty when we simply have not looked yet.
 */
@Composable
fun SavedSearchesScreen(
    onBack: () -> Unit,
    onRunSearch: (JobFilters) -> Unit,
) {
    val network = networkModule()
    val viewModel: SavedSearchesViewModel = viewModel(
        factory = viewModelFactory { initializer { SavedSearchesViewModel(network.jobHuntRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val actionError = (state as? SavedSearchesUiState.Success)?.actionError
    LaunchedEffect(actionError) {
        if (actionError != null) {
            snackbarHostState.showSnackbar(actionError)
            viewModel.clearActionError()
        }
    }

    Scaffold(
        topBar = { DetailTopBar(title = "Saved searches", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is SavedSearchesUiState.Loading ->
                    SkeletonList(count = 3, modifier = Modifier.fillMaxSize().padding(Spacing.md))

                is SavedSearchesUiState.Failed -> FailureState(
                    failure = current.failure,
                    onRetry = viewModel::retry,
                    // A saved-searches list has nowhere useful to send someone for setup or
                    // an upgrade, so both fall back to a plain retry rather than a dead end.
                    onOpenSetup = viewModel::retry,
                    onOpenSubscriptions = viewModel::retry,
                    onBack = onBack,
                )

                is SavedSearchesUiState.Success -> if (current.isEmpty) {
                    EmptyState(
                        title = "No saved searches yet",
                        message = "Set up filters on the jobs screen and save them here, " +
                            "so you can run the same search again without rebuilding it.",
                        icon = Icons.Filled.BookmarkBorder,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        items(current.searches, key = { it.id }) { search ->
                            SavedSearchRow(
                                search = search,
                                isDeleting = search.id in current.deletingIds,
                                onRun = { onRunSearch(search.filters.toFilters()) },
                                onDelete = { viewModel.delete(search.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedSearchRow(
    search: SavedSearchDto,
    isDeleting: Boolean,
    onRun: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = search.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = savedSearchSummary(search),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                // Only when it has actually run. See savedSearchLastRunLabel.
                val lastRun = savedSearchLastRunLabel(search)
                if (lastRun != null) {
                    Text(
                        text = lastRun,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
            IconButton(onClick = onDelete, enabled = !isDeleting) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete saved search ${search.name}",
                )
            }
        }
        SecondaryButton(
            text = "Run this search",
            onClick = onRun,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
    }
}
