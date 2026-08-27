package com.aiplacementos.app.ui.atscheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.CategoryBreakdownDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor

@Composable
fun AtsCheckCategoryDetailScreen(checkId: Int, category: String, onBack: () -> Unit) {
    val network = networkModule()
    val viewModel: AtsCheckCategoryDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AtsCheckCategoryDetailViewModel(checkId, category, network.atsCheckRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { DetailTopBar(title = atsCategoryLabel(category), onBack = onBack) }) { padding ->
        when (val current = state) {
            is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            is UiState.Error -> ErrorState(
                message = current.message,
                onRetry = viewModel::load,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Empty -> ErrorState(message = "No data available.", modifier = Modifier.fillMaxSize().padding(padding))
            is UiState.Success<CategoryBreakdownDto> -> CategoryDetailContent(current.data, Modifier.padding(padding))
        }
    }
}

@Composable
private fun CategoryDetailContent(category: CategoryBreakdownDto, modifier: Modifier = Modifier) {
    val band = scoreCategoryForScore(category.score)
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(
                        percent = category.score,
                        displayText = "${category.score}/100",
                        size = 88.dp,
                        ringColor = scoreBandColor(band),
                    )
                    Column(modifier = Modifier.padding(start = Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = scoreBandColor(band), modifier = Modifier.size(18.dp))
                            Text(
                                text = scoreBandLabel(band),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                        Text(
                            text = category.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
        }
        if (category.strengths.isNotEmpty()) {
            item { FindingListCard(title = "What's Good", items = category.strengths, color = successColor()) }
        }
        if (category.improvements.isNotEmpty()) {
            item { FindingListCard(title = "Needs Improvement", items = category.improvements, color = warningColor()) }
        }
        if (category.howToImprove.isNotEmpty()) {
            item {
                Text(text = "How to Improve", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Column(modifier = Modifier.padding(top = Spacing.sm)) {
                    category.howToImprove.forEach { tip ->
                        AppCard(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs)) {
                            Text(text = tip, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindingListCard(title: String, items: List<String>, color: androidx.compose.ui.graphics.Color) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color)
        Column(modifier = Modifier.padding(top = Spacing.sm)) {
            items.forEach { line ->
                Text(
                    text = "• $line",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

private fun scoreCategoryForScore(score: Int): String = when {
    score >= 90 -> "excellent"
    score >= 80 -> "very_good"
    score >= 70 -> "good"
    score >= 60 -> "needs_improvement"
    else -> "needs_attention"
}
