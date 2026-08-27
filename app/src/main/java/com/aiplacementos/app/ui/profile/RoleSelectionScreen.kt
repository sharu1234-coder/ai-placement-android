package com.aiplacementos.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun RoleSelectionScreen(
    isSecondary: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    onRoleSelected: () -> Unit,
) {
    val network = networkModule()
    val viewModel: RoleSelectionViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RoleSelectionViewModel(network.profileRepository, isSecondary) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var customRoleText by remember { mutableStateOf("") }
    var showCustomEntry by remember { mutableStateOf(false) }

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
        is UiState.Empty -> EmptyState(message = "No roles available yet.", modifier = Modifier.fillMaxSize())
        is UiState.Success<List<RoleDto>> -> RoleList(
            header = header,
            roles = current.data,
            query = query,
            onQueryChange = { query = it },
            allowCustomRole = viewModel.allowCustomRole,
            showCustomEntry = showCustomEntry,
            onToggleCustomEntry = { showCustomEntry = !showCustomEntry },
            customRoleText = customRoleText,
            onCustomRoleTextChange = { customRoleText = it },
            onSubmitCustomRole = { viewModel.selectCustomRole(customRoleText, onSelected = onRoleSelected) },
            onRoleClick = { role -> viewModel.selectRole(role.id, onSelected = onRoleSelected) },
        )
    }
}

@Composable
private fun RoleList(
    header: (@Composable () -> Unit)?,
    roles: List<RoleDto>,
    query: String,
    onQueryChange: (String) -> Unit,
    allowCustomRole: Boolean,
    showCustomEntry: Boolean,
    onToggleCustomEntry: () -> Unit,
    customRoleText: String,
    onCustomRoleTextChange: (String) -> Unit,
    onSubmitCustomRole: () -> Unit,
    onRoleClick: (RoleDto) -> Unit,
) {
    val filtered = remember(roles, query) {
        if (query.isBlank()) roles else roles.filter { it.name.contains(query, ignoreCase = true) }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { it.category ?: "Other" }.toSortedMap()
    }

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        header?.invoke()
        Text(text = "Select your target role", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search roles") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            grouped.forEach { (category, categoryRoles) ->
                item(key = "header-$category") {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
                    )
                }
                items(categoryRoles, key = { it.id }) { role ->
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRoleClick(role) },
                    ) {
                        Text(text = role.name, style = MaterialTheme.typography.titleMedium)
                        role.description?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (allowCustomRole) {
                item(key = "custom-role") {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                        if (showCustomEntry) {
                            OutlinedTextField(
                                value = customRoleText,
                                onValueChange = onCustomRoleTextChange,
                                label = { Text("Your target role") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            PrimaryButton(
                                text = "Use this role",
                                onClick = onSubmitCustomRole,
                                enabled = customRoleText.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                            )
                        } else {
                            SecondaryButton(
                                text = "Don't see your role? Enter your own",
                                onClick = onToggleCustomEntry,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
