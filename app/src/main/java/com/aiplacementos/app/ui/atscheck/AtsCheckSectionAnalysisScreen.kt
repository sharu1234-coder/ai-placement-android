package com.aiplacementos.app.ui.atscheck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.SectionAnalysisDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor

@Composable
fun AtsCheckSectionAnalysisScreen(checkId: Int, onOpenCategory: (String) -> Unit, onBack: () -> Unit) {
    val network = networkModule()
    val viewModel: AtsCheckSectionAnalysisViewModel = viewModel(
        factory = viewModelFactory { initializer { AtsCheckSectionAnalysisViewModel(checkId, network.atsCheckRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { DetailTopBar(title = "Section Analysis", onBack = onBack) }) { padding ->
        when (val current = state) {
            is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            is UiState.Error -> ErrorState(
                message = current.message,
                onRetry = viewModel::load,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Empty -> EmptyState(message = "No section data available.", modifier = Modifier.fillMaxSize().padding(padding))
            is UiState.Success<List<SectionAnalysisDto>> -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(current.data, key = { it.section }) { section ->
                    SectionRow(section = section, onClick = { onOpenCategory(categoryForSection(section.section)) })
                }
            }
        }
    }
}

@Composable
private fun SectionRow(section: SectionAnalysisDto, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = sectionLabel(section.section), style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(status = section.status)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (label, color) = when (status) {
        "complete" -> "Complete" to successColor()
        "good" -> "Good" to MaterialTheme.colorScheme.primary
        "needs_improvement" -> "Needs Improvement" to warningColor()
        else -> "Missing" to MaterialTheme.colorScheme.error
    }
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
        )
    }
}
