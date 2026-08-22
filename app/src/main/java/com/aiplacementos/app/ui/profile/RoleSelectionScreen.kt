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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun RoleSelectionScreen(onRoleSelected: () -> Unit) {
    val network = networkModule()
    val viewModel: RoleSelectionViewModel = viewModel(
        factory = viewModelFactory { initializer { RoleSelectionViewModel(network.profileRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize())
        is UiState.Empty -> EmptyState(message = "No roles available yet.", modifier = Modifier.fillMaxSize())
        is UiState.Success<List<RoleDto>> -> RoleList(
            roles = current.data,
            onRoleClick = { role -> viewModel.selectRole(role.id, onSelected = onRoleSelected) },
        )
    }
}

@Composable
private fun RoleList(roles: List<RoleDto>, onRoleClick: (RoleDto) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        Text(text = "Select your target role", style = MaterialTheme.typography.headlineSmall)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(roles, key = { it.id }) { role ->
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
    }
}
