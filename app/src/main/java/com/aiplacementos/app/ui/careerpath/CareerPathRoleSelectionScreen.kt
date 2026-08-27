package com.aiplacementos.app.ui.careerpath

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
        factory = viewModelFactory { initializer { CareerPathRoleSelectionViewModel(network.careerPathRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                        onSelectRole = { text -> viewModel.selectCustomRole(text, onGenerated) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleSelectionContent(
    state: CareerPathRoleSelectionUiState.Loaded,
    // Takes the role's display text either way -- a catalog card's exact display_name (which
    // the backend resolves back to the same curated family via an exact-name match) or a
    // freely typed custom role. RoleCatalogEntryDto has no numeric role_id of its own (it's
    // keyed by a family_key slug, not the unrelated DB Role table), so this is the one
    // request shape that correctly covers both.
    onSelectRole: (roleName: String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var customRoleText by remember { mutableStateOf("") }

    val filteredRoles = state.roles.filter { role ->
        (selectedCategory == null || role.category == selectedCategory) &&
            (query.isBlank() || role.displayName.contains(query, ignoreCase = true))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

@Composable
private fun RoleCard(role: RoleCatalogEntryDto, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text(text = role.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(text = role.shortExplanation, style = MaterialTheme.typography.bodyMedium)
    }
}
