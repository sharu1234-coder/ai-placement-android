package com.aiplacementos.app.ui.careerpath

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.RoleCatalogEntryDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.BuildingStateCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

private val BUILDING_MESSAGES = listOf(
    "Analyzing your profile...",
    "Matching role requirements...",
    "Building your career path...",
)

@Composable
fun CareerPathRoleSelectionScreen(onBack: () -> Unit, onGenerated: (pathId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: CareerPathRoleSelectionViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CareerPathRoleSelectionViewModel(
                    network.careerPathRepository,
                    // The file-backed store: a generation's record has to outlive this process.
                    CareerPathGenerationRecovery(network.careerPathRepository, network.pendingOperationStore),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val generatedPathId by viewModel.generatedPathId.collectAsStateWithLifecycle()

    // Navigates from whichever composition of this screen is current, never from one that was
    // current when the role was tapped -- see CareerPathRoleSelectionViewModel.generatedPathId.
    LaunchedEffect(generatedPathId) {
        val pathId = generatedPathId ?: return@LaunchedEffect
        viewModel.onGeneratedPathOpened()
        onGenerated(pathId)
    }

    // Same idiom the other return-to screens use (ArenaListScreen, CareerGoalListScreen).
    // Here it does not reload anything: it retires a finished generation's building state,
    // which is what Back from the detail screen used to come back to, and it bounds looking for
    // a still-finishing generation's result to the time the screen is actually visible.
    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose { viewModel.onScreenPaused() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = "Choose Your Target Role", onBack = onBack)
        when (val current = state) {
            is CareerPathRoleSelectionUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
            is CareerPathRoleSelectionUiState.Error -> ErrorState(
                message = current.message,
                modifier = Modifier.fillMaxSize(),
                onRetry = viewModel::load,
            )
            is CareerPathRoleSelectionUiState.Loaded -> {
                if (current.isGenerating) {
                    BuildingStateCard(messages = BUILDING_MESSAGES, modifier = Modifier.fillMaxSize())
                } else {
                    RoleSelectionContent(
                        state = current,
                        onSelectRole = { text -> viewModel.selectCustomRole(text) },
                    )
                }
            }
            is CareerPathRoleSelectionUiState.StillFinishing -> StillFinishingState(
                role = current.role,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * A generation whose response was lost, not refused: the server may still be building the path,
 * and the view model is looking for it. Offers nothing to tap on purpose -- a second request
 * while the first may still be running is exactly the overlap F12-03 is about.
 */
@Composable
private fun StillFinishingState(role: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CircularProgressIndicator()
        Text(
            text = "Still finishing your $role Career Path",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            text = "Building a path can take a few minutes. You can leave this screen -- " +
                "it will look for your path again when you come back.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Internal rather than private so a rendered test can drive it directly.
 *
 * The view-model tests already proved a failed generation lands in [state]'s generationError --
 * and it did that before this screen read the field at all. The state was correct and the
 * student still saw nothing, so only a rendered test says anything useful here.
 */
@Composable
internal fun RoleSelectionContent(
    state: CareerPathRoleSelectionUiState.Loaded,
    // Takes the role's display text either way -- a catalog card's exact display_name (which
    // the backend resolves back to the same curated family via an exact-name match) or a
    // freely typed custom role. RoleCatalogEntryDto has no numeric role_id of its own (it's
    // keyed by a family_key slug, not the unrelated DB Role table), so this is the one
    // request shape that correctly covers both.
    onSelectRole: (roleName: String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var customRoleText by rememberSaveable { mutableStateOf("") }

    // Hoisted into locals so the null checks below smart-cast inside the item lambdas.
    val generationError = state.generationError
    val unconfirmedMessage = state.unconfirmedMessage

    val filteredRoles = remember(state.roles, selectedCategory, query) {
        state.roles.filter { role ->
            (selectedCategory == null || role.category == selectedCategory) &&
                (query.isBlank() || role.displayName.contains(query, ignoreCase = true))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // A generation failure keeps the student here with the screen's own controls
        // intact, so it is reported inline rather than by swapping the screen for a global
        // error state: retrying is the same generation action, and generate() clears this
        // message itself when it starts. First item because the list re-enters from the
        // generating branch scrolled to the top -- a banner further down would not be read.
        if (generationError != null) {
            item {
                AppCard(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = generationError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        // Announced by screen readers the moment it appears, rather than only
                        // being noticed by a sighted user looking at the top of the list.
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    // Names no particular control on purpose. It answers the only question a
                    // failure raises -- whether the student has to leave and come back -- and
                    // stays true whichever control they reach for.
                    Text(
                        text = "You can try again from here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        // Not the error card: nothing confirmed that the generation failed, only that its
        // result did not appear before the deadline -- see GenerationOutcome.Unconfirmed.
        if (unconfirmedMessage != null) {
            item {
                AppCard(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = unconfirmedMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
        item {
            Text(
                text = "Select a role or search for any career you want to pursue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search roles...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            // chipGap (not sm) between these -- see Spacing.chipGap's own doc comment.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All") },
                    )
                }
                items(state.categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = if (selectedCategory == category) null else category },
                        label = { Text(category) },
                    )
                }
            }
        }
        items(filteredRoles, key = { it.familyKey }) { role ->
            RoleCard(role = role, onClick = { onSelectRole(role.displayName) })
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
            Text(text = "Can't find your role?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = customRoleText,
                onValueChange = { customRoleText = it },
                label = { Text("Enter a custom role") },
                placeholder = { Text("e.g. Blockchain Developer") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            PrimaryButton(
                text = "Create a personalized path for any role",
                onClick = { if (customRoleText.isNotBlank()) onSelectRole(customRoleText.trim()) },
                enabled = customRoleText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * One curated role in the catalog, and the screen's primary way to start a Career Path.
 *
 * This took an `onClick` and never invoked it: AppCard is a plain container with no click
 * handling of its own, and nothing here made the surface interactive. The cards rendered
 * perfectly and did nothing when tapped, so the only working path to a Career Path was typing
 * a role by hand into the custom-role field at the bottom of the list.
 *
 * The fix is the pattern the rest of the app already uses for a list of tappable cards --
 * clickable on the card's own modifier, not a change to AppCard, which a dozen non-interactive
 * callers share.
 */
@Composable
private fun RoleCard(role: RoleCatalogEntryDto, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            // 48dp is Android's minimum touch target; a role with a one-line explanation can
            // otherwise render shorter than that at a small font scale. Same treatment as
            // CareerGoalListScreen's CareerGoalCard, the closest existing list-of-taps.
            .heightIn(min = 48.dp)
            // Role.Button so TalkBack announces it as actionable rather than reading two
            // lines of text with no hint that they do anything. clickable merges the card's
            // descendants, so the announcement already carries the role's own name.
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(text = role.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(text = role.shortExplanation, style = MaterialTheme.typography.bodyMedium)
    }
}
