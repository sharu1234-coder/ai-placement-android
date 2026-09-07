package com.aiplacementos.app.ui.jobhunt

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobHuntResultsScreen(
    onBack: () -> Unit,
    onOpenJob: (Int) -> Unit,
    onOpenSaved: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenSubscriptions: () -> Unit,
) {
    val network = networkModule()
    val viewModel: JobHuntSearchViewModel = viewModel(
        factory = viewModelFactory { initializer { JobHuntSearchViewModel(network.jobHuntRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // rememberSaveable: a rotation while the sheet is open should not close it.
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val activeFilters = (state as? JobSearchUiState.Success)?.filters ?: JobFilters()

    val actionError = (state as? JobSearchUiState.Success)?.actionError
    LaunchedEffect(actionError) {
        if (actionError != null) {
            snackbarHostState.showSnackbar(actionError)
            viewModel.clearActionError()
        }
    }

    Scaffold(
        topBar = {
            DetailTopBar(
                title = "Recommended Jobs",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showFilters = true }) {
                        Icon(
                            Icons.Filled.FilterList,
                            // The count is spoken, not just drawn, so a screen-reader user
                            // knows filters are in effect without opening the sheet.
                            contentDescription = if (activeFilters.activeCount > 0) {
                                "Filters, ${activeFilters.activeCount} applied"
                            } else {
                                "Filters"
                            },
                        )
                    }
                    IconButton(onClick = onOpenSaved) {
                        Icon(Icons.Filled.Bookmarks, contentDescription = "Saved jobs")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (showFilters) {
            JobFiltersSheet(
                initial = activeFilters,
                onApply = {
                    viewModel.applyFilters(it)
                    showFilters = false
                },
                onClear = {
                    viewModel.clearFilters()
                    showFilters = false
                },
                onDismiss = { showFilters = false },
            )
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FilterTabs(
                active = (state as? JobSearchUiState.Success)?.activeTab ?: JobFilterTab.All,
                onSelect = viewModel::onTabSelected,
            )

            when (val current = state) {
                is JobSearchUiState.Loading ->
                    SkeletonList(count = 4, modifier = Modifier.fillMaxSize().padding(Spacing.md))

                is JobSearchUiState.Failed -> FailureState(
                    failure = current.failure,
                    onRetry = viewModel::retry,
                    onOpenSetup = onOpenSetup,
                    onOpenSubscriptions = onOpenSubscriptions,
                )

                is JobSearchUiState.Success -> if (current.isEmpty) {
                    // Only reachable when the provider genuinely answered with nothing. Every
                    // failure path lands in FailureState above, so this message is never shown
                    // for a problem on our side.
                    EmptyState(
                        title = "No jobs matched",
                        message = "We searched, but nothing matched these filters right now. " +
                            "Try widening your role or location.",
                        icon = Icons.Filled.SearchOff,
                        actionLabel = "Edit search",
                        onAction = onOpenSetup,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ResultsList(state = current, viewModel = viewModel, onOpenJob = onOpenJob)
                }
            }
        }
    }
}

@Composable
private fun FilterTabs(active: JobFilterTab, onSelect: (JobFilterTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
    ) {
        JobFilterTab.entries.forEach { tab ->
            FilterChip(
                selected = tab == active,
                onClick = { onSelect(tab) },
                label = { Text(tab.label) },
            )
        }
    }
}

@Composable
private fun ResultsList(
    state: JobSearchUiState.Success,
    viewModel: JobHuntSearchViewModel,
    onOpenJob: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column {
                Text(
                    text = "Showing jobs matched for you",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                // The provider's own total, when it reports one. Never substituted with the
                // page size, which would make one page look like the whole market.
                if (state.totalAvailable != null) {
                    Text(
                        text = "${state.totalAvailable} listings found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(state.results, key = { it.listing.id }) { result ->
            JobCard(
                listing = result.listing,
                match = result.match,
                isSaved = state.savedListingIds.containsKey(result.listing.id),
                isSaving = result.listing.id in state.savingListingIds,
                onOpen = { onOpenJob(result.listing.id) },
                onToggleSave = { viewModel.toggleSave(result.listing.id) },
            )
        }

        item {
            // Several providers make this a contractual condition of displaying their
            // listings, so it ships with the results rather than being optional chrome.
            //
            // Adzuna's terms require the attribution to be a *hyperlink* to their site, not
            // plain text, so this is rendered as a link whenever the backend supplies a URL
            // and falls back to plain text when it does not (some providers require only the
            // wording). Rendering it as text everywhere was a licensing failure, not a
            // styling one.
            if (state.attributionText.isNotBlank()) {
                val attributionUrl = state.attributionUrl
                if (attributionUrl != null) {
                    val context = LocalContext.current
                    Text(
                        text = state.attributionText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .padding(top = Spacing.sm)
                            // 48dp so the link is a real touch target, not a 14sp line of text.
                            .heightIn(min = 48.dp)
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, attributionUrl.toUri())
                                    )
                                }
                            }
                            .semantics {
                                role = Role.Button
                                contentDescription =
                                    "${state.attributionText}. Opens the provider's website."
                            },
                    )
                } else {
                    Text(
                        text = state.attributionText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }
    }
}

/**
 * Each failure class gets its own icon, message and affordance.
 *
 * The important one is [JobSearchFailure.Unavailable]: offering a "Try again" button there
 * would be a lie, because no amount of retrying configures a job provider. So that state gets
 * no retry at all.
 */
@Composable
private fun FailureState(
    failure: JobSearchFailure,
    onRetry: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenSubscriptions: () -> Unit,
) {
    when (failure) {
        is JobSearchFailure.Unavailable -> EmptyState(
            title = "Job Hunt isn't live yet",
            message = failure.message,
            icon = Icons.Filled.CloudOff,
            modifier = Modifier.fillMaxSize(),
        )

        is JobSearchFailure.PremiumRequired -> Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp).padding(bottom = Spacing.sm),
            )
            Text(text = failure.message, style = MaterialTheme.typography.bodyLarge)
            PrimaryButton(
                text = "See Premium",
                onClick = onOpenSubscriptions,
                modifier = Modifier.padding(top = Spacing.md),
            )
            SecondaryButton(
                text = "Back to all jobs",
                onClick = onRetry,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        is JobSearchFailure.SetupRequired -> EmptyState(
            title = "Tell us what you're looking for",
            message = failure.message,
            icon = Icons.Filled.SearchOff,
            actionLabel = "Set up Job Hunt",
            onAction = onOpenSetup,
            modifier = Modifier.fillMaxSize(),
        )

        is JobSearchFailure.Transient -> ErrorState(
            message = failure.message,
            icon = if (failure.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
