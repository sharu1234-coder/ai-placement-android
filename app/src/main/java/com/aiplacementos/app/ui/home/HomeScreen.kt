package com.aiplacementos.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun HomeScreen(
    onOpenPlacementAlarm: () -> Unit,
    onOpenPlacementPath: () -> Unit,
    onOpenJdAnalyzer: () -> Unit,
    onOpenCareerPath: (pathId: Int) -> Unit,
) {
    val network = networkModule()
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    network.placementPathRepository,
                    network.progressRepository,
                    network.placementAlarmRepository,
                    network.roadmapRepository,
                    network.careerPathRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
        is UiState.Empty -> EmptyState(message = "Nothing to show yet.", modifier = Modifier.fillMaxSize())
        is UiState.Success<HomeData> -> HomeContent(
            data = current.data,
            onOpenPlacementAlarm = onOpenPlacementAlarm,
            onOpenPlacementPath = onOpenPlacementPath,
            onOpenJdAnalyzer = onOpenJdAnalyzer,
            onOpenCareerPath = onOpenCareerPath,
        )
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    onOpenPlacementAlarm: () -> Unit,
    onOpenPlacementPath: () -> Unit,
    onOpenJdAnalyzer: () -> Unit,
    onOpenCareerPath: (pathId: Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Text(text = data.greeting, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Let's get you placement-ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { PlacementReadinessCard(data, onOpenPlacementPath) }

        // Deliberately its own card, not merged into PlacementReadinessCard above -- Career
        // Path is an independent feature with its own progress model.
        val careerPath = data.careerPath
        if (careerPath != null) {
            item { YourCareerPathCard(careerPath, onClick = { onOpenCareerPath(careerPath.id) }) }
        }

        if (data.todaysPlan.isNotEmpty()) {
            item { TodaysPlanCard(data.todaysPlan) }
        }

        item { JdAnalyzerPromoCard(onOpenJdAnalyzer) }

        if (data.upcomingAlarmLabel != null) {
            item { UpcomingAlarmCard(data.upcomingAlarmLabel, onOpenPlacementAlarm) }
        }

        if (data.currentStreakDays > 0) {
            item { StreakCard(data.currentStreakDays) }
        }

        if (data.recommendedForYou.isNotEmpty()) {
            item {
                Text(
                    text = "Recommended For You",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            items(data.recommendedForYou) { tip ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = tip, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (data.recentActivity.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            items(data.recentActivity) { activity ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = activity.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${activity.scorePercent}%",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlacementReadinessCard(data: HomeData, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = "Placement Readiness",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
            if (data.readinessPercent != null) {
                ScoreRing(percent = data.readinessPercent, size = 72.dp)
            } else {
                Icon(
                    imageVector = Icons.Filled.Insights,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp),
                )
            }
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                data.subMetrics.forEach { metric ->
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "${metric.label}: ${metric.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(start = Spacing.xs, top = 1.dp),
                        )
                    }
                }
            }
        }
        Text(
            text = data.nextBestActionTitle?.let { "Today's focus: $it" }
                ?: "Generate your personalized roadmap to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/** "How do I become job-ready for role X" -- a distinct question from PlacementReadinessCard
 * above ("what should I work on in my current learning plan"), so this is its own card
 * sourced from an entirely separate repository/state, never merged into that one. */
@Composable
private fun YourCareerPathCard(careerPath: CareerPathDto, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "Your Career Path",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
            ScoreRing(percent = careerPath.jobReadiness.overallPercent, size = 56.dp)
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(
                    text = careerPath.displayRoleName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "${careerPath.jobReadiness.overallPercent}% job ready",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Text(
            text = "Continue Preparation",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun TodaysPlanCard(items: List<TodayPlanItem>) {
    Column {
        Text(text = "Today's Plan", style = MaterialTheme.typography.titleMedium)
        AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
            items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Spacing.xs)) {
                    Icon(
                        imageVector = if (item.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (item.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.padding(start = Spacing.sm)) {
                        Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = item.taskType,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (index != items.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                }
            }
        }
    }
}

@Composable
private fun JdAnalyzerPromoCard(onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.WorkOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(
                    text = "Prepare for a Job",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "Paste any company's job description and get a personalized preparation plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun UpcomingAlarmCard(label: String, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.Alarm, contentDescription = null)
            Column(modifier = Modifier.padding(start = Spacing.sm)) {
                Text(text = "Upcoming alarm", style = MaterialTheme.typography.titleSmall)
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StreakCard(days: Int) {
    AppCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.padding(start = Spacing.sm)) {
                Text(
                    text = "$days day${if (days == 1) "" else "s"} streak",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = "Keep it up!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}
