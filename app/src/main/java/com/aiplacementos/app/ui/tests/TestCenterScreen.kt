package com.aiplacementos.app.ui.tests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.aiplacementos.app.data.remote.AssessmentHistoryItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TestCenterScreen(onStartTest: (blueprintId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: TestCenterViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                TestCenterViewModel(network.assessmentRepository, network.progressRepository, network.profileRepository)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val startingCategory by viewModel.startingCategory.collectAsStateWithLifecycle()
    val startError by viewModel.startError.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(message = current.message, onRetry = viewModel::load, modifier = Modifier.fillMaxSize())
        is UiState.Success<TestCenterData> -> TestCenterContent(
            data = current.data,
            startingCategory = startingCategory,
            startError = startError,
            onStartCategory = { category -> viewModel.startCategory(category, onBlueprintReady = onStartTest) },
        )
    }
}

@Composable
private fun TestCenterContent(
    data: TestCenterData,
    startingCategory: String?,
    startError: String?,
    onStartCategory: (TestCategory) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Text(text = "Test Center", style = MaterialTheme.typography.headlineSmall) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    label = "Average Score",
                    value = data.averageScorePercent?.let { "$it%" } ?: "--",
                    modifier = Modifier.weight(1f),
                )
                StatCard(label = "Tests Completed", value = "${data.testsCompleted}", modifier = Modifier.weight(1f))
                StatCard(
                    label = "Accuracy",
                    value = data.accuracyPercent?.let { "$it%" } ?: "--",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (data.weakAreas.isNotEmpty()) {
            item {
                AppCard(containerColor = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Weak Areas",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = data.weakAreas.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }

        item {
            Column {
                Text(text = "Categories", style = MaterialTheme.typography.titleMedium)
                if (startError != null) {
                    Text(
                        text = startError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).height(72.dp * (TEST_CATEGORIES.size / 2 + 1)),
            ) {
                items(TEST_CATEGORIES) { category ->
                    CategoryCard(
                        category = category,
                        isBusy = startingCategory == category.label,
                        onClick = { onStartCategory(category) },
                    )
                }
            }
        }

        if (data.recentResults.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Results",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            lazyColumnItems(data.recentResults) { result -> RecentResultRow(result) }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryCard(category: TestCategory, isBusy: Boolean, onClick: () -> Unit) {
    AppCard(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isBusy, onClick = onClick),
    ) {
        Text(
            text = if (isBusy) "Starting..." else category.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun RecentResultRow(result: AssessmentHistoryItemDto) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = categoryLabel(result.category), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${(result.accuracy * 100).roundToInt()}% accuracy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ScorePill(scorePercent = (result.overallScore * 100).roundToInt())
        }
    }
}

@Composable
private fun ScorePill(scorePercent: Int) {
    val (label, color) = when {
        scorePercent >= 75 -> "Good" to successColor()
        scorePercent >= 50 -> "Average" to warningColor()
        else -> "Needs Work" to MaterialTheme.colorScheme.error
    }
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
        )
    }
}

private fun categoryLabel(assessmentType: String): String = when (assessmentType) {
    "coding" -> "DSA / Programming"
    "sql" -> "SQL"
    "aptitude" -> "Aptitude"
    "technical" -> "Technical / Core CS"
    "case_study" -> "Case Study"
    "statistics" -> "Statistics"
    "tool" -> "Tools"
    else -> assessmentType.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}
