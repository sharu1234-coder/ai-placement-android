package com.aiplacementos.app.ui.careerpath

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.SkillGapItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.IconChip
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.StatusPill
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

private val FILTERS = listOf("all", "strong", "needs_improvement", "missing", "recommended")

@Composable
fun CareerPathGapAnalysisScreen(pathId: Int, onBack: () -> Unit) {
    val network = networkModule()
    val viewModel: CareerPathGapAnalysisViewModel = viewModel(
        factory = viewModelFactory { initializer { CareerPathGapAnalysisViewModel(pathId, network.careerPathRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = "Skill Gap Analysis", onBack = onBack)
        when (val current = state) {
            is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
            is UiState.Empty -> EmptyState(
                title = "No gaps recorded",
                message = "Your skill gaps appear here once your resume has been compared against the role.",
                modifier = Modifier.fillMaxSize(),
            )
            is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize(), onRetry = viewModel::load)
            is UiState.Success -> GapAnalysisContent(gaps = current.data.skillGaps)
        }
    }
}

@Composable
private fun GapAnalysisContent(gaps: List<SkillGapItemDto>) {
    var selectedFilter by remember { mutableStateOf("all") }
    val counts = FILTERS.associateWith { f -> if (f == "all") gaps.size else gaps.count { it.status == f } }
    val selectedIndex = FILTERS.indexOf(selectedFilter)
    val filtered = remember(gaps, selectedFilter) {
        if (selectedFilter == "all") gaps else gaps.filter { it.status == selectedFilter }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedIndex) {
            FILTERS.forEach { filter ->
                Tab(
                    selected = filter == selectedFilter,
                    onClick = { selectedFilter = filter },
                    text = {
                        Text(
                            if (filter == "all") "All (${counts[filter]})" else "${skillGapStatusLabel(filter)} (${counts[filter]})"
                        )
                    },
                )
            }
        }
        if (filtered.isEmpty()) {
            EmptyState(
                title = "Nothing in this category",
                message = "Choose another tab to see the rest of your skills.",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(filtered, key = { it.skill + it.status }) { gap ->
                    GapCard(gap = gap)
                }
            }
        }
    }
}

@Composable
private fun GapCard(gap: SkillGapItemDto) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            // The chip gives the loose 20dp glyph a ground, so the rows share a left edge the
            // eye can run down instead of four differently-shaped icons floating in a list.
            IconChip(
                icon = when (gap.status) {
                    "strong" -> Icons.Filled.CheckCircle
                    "needs_improvement" -> Icons.Filled.Warning
                    "missing" -> Icons.Filled.Circle
                    else -> Icons.Filled.Lightbulb
                },
                tint = skillGapStatusColor(gap.status),
                size = 34.dp,
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = gap.skill,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                // The status as a pill rather than coloured text: the word still carries the
                // meaning, and the ground makes it read as a state rather than as a subtitle.
                StatusPill(
                    label = skillGapStatusLabel(gap.status),
                    color = skillGapStatusColor(gap.status),
                    modifier = Modifier.padding(top = 2.dp),
                )
                gap.evidence?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
    }
}
