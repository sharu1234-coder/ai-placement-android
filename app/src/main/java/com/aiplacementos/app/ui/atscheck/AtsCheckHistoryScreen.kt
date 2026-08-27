package com.aiplacementos.app.ui.atscheck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.AtsCheckListItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.ScoreHistoryChart
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AtsCheckHistoryScreen(onOpenCheck: (checkId: Int) -> Unit, onBack: () -> Unit) {
    val network = networkModule()
    val viewModel: AtsCheckHistoryViewModel = viewModel(
        factory = viewModelFactory { initializer { AtsCheckHistoryViewModel(network.atsCheckRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { DetailTopBar(title = "Score History", onBack = onBack) }) { padding ->
        when (val current = state) {
            is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            is UiState.Error -> ErrorState(
                message = current.message,
                onRetry = viewModel::load,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Empty -> EmptyState(
                message = "No ATS checks yet. Run your first check to start tracking progress.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Success<List<AtsCheckListItemDto>> -> HistoryContent(
                checks = current.data,
                onOpenCheck = onOpenCheck,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun HistoryContent(checks: List<AtsCheckListItemDto>, onOpenCheck: (Int) -> Unit, modifier: Modifier = Modifier) {
    val oldestFirst = checks.reversed()
    val improvement = if (oldestFirst.size >= 2) oldestFirst.last().overallScore - oldestFirst.first().overallScore else null

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (oldestFirst.size >= 2) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Your Progress", style = MaterialTheme.typography.titleMedium)
                    if (improvement != null) {
                        Text(
                            text = if (improvement >= 0) "+$improvement" else "$improvement",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (improvement >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                ScoreHistoryChart(
                    scores = oldestFirst.map { it.overallScore },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                )
            }
        }
        item {
            Text(
                text = "Analysis History",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        items(checks, key = { it.id }) { check -> HistoryRow(check = check, onClick = { onOpenCheck(check.id) }) }
    }
}

@Composable
private fun HistoryRow(check: AtsCheckListItemDto, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(text = check.resumeFilename ?: "Resume", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = formatCreatedAt(check.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${check.overallScore}/100",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scoreBandColor(check.scoreCategory),
            )
        }
    }
}

private fun formatCreatedAt(createdAt: String): String = try {
    OffsetDateTime.parse(createdAt).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
} catch (e: Exception) {
    createdAt
}
