package com.aiplacementos.app.ui.careerpath

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.CareerPathSummaryDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ConfirmationDialog
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.SkillBar
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun CareerPathSavedRolesScreen(onBack: () -> Unit, onOpenPath: (pathId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: CareerPathSavedRolesViewModel = viewModel(
        factory = viewModelFactory { initializer { CareerPathSavedRolesViewModel(network.careerPathRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    var pendingSwitchTo by remember { mutableStateOf<CareerPathSummaryDto?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = "Saved Career Paths", onBack = onBack)
        when (val current = state) {
            is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
            is UiState.Empty -> EmptyState(message = "No saved roles yet.", modifier = Modifier.fillMaxSize())
            is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize(), onRetry = viewModel::load)
            is UiState.Success -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(current.data, key = { it.id }) { summary ->
                    SavedRoleCard(
                        summary = summary,
                        onOpen = { onOpenPath(summary.id) },
                        onSetCurrent = { pendingSwitchTo = summary },
                    )
                }
            }
        }
    }

    pendingSwitchTo?.let { target ->
        ConfirmationDialog(
            title = "Switch Career Path?",
            message = "\"${target.displayRoleName}\" will become your current Career Path. " +
                "Nothing is deleted -- you can switch back anytime.",
            confirmText = "Switch",
            onConfirm = {
                viewModel.setCurrent(target.id)
                pendingSwitchTo = null
            },
            onDismiss = { pendingSwitchTo = null },
        )
    }
}

@Composable
private fun SavedRoleCard(summary: CareerPathSummaryDto, onOpen: () -> Unit, onSetCurrent: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = summary.displayRoleName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "${summary.completedItems} of ${summary.totalItems} complete (${summary.readinessPercent}%)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (summary.isPrimary) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        text = "PRIMARY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
                    )
                }
            } else {
                Text(
                    text = "Set as Primary",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onSetCurrent),
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        SkillBar(label = "Progress", percent = summary.readinessPercent)
    }
}
