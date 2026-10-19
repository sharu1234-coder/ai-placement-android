package com.aiplacementos.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ProgressDashboardDto
import com.aiplacementos.app.data.remote.ProgressTrendPointDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.GrowthIllustration
import com.aiplacementos.app.ui.common.HeroCard
import com.aiplacementos.app.ui.common.IconChip
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.ScreenHeader
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SectionHeader
import com.aiplacementos.app.ui.common.StatChip
import com.aiplacementos.app.ui.common.StatTile
import com.aiplacementos.app.ui.common.StatusPill
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.accentCyanColor
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor
import kotlin.math.roundToInt

/**
 * [onOpenTests] lets the two empty states point at the one action that would fill them.
 * Optional, so a caller with no tests destination to hand off to renders the explanation
 * alone rather than a button that goes nowhere.
 */
@Composable
fun ProgressDashboardScreen(onOpenTests: (() -> Unit)? = null) {
    val network = networkModule()
    val viewModel: ProgressDashboardViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ProgressDashboardViewModel(network.progressRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize(), label = "Loading your progress...")
        is UiState.Error -> ErrorState(
            message = current.message,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::load,
        )
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Success<ProgressDashboardDto> -> DashboardContent(current.data, onOpenTests)
    }
}

@Composable
private fun DashboardContent(dashboard: ProgressDashboardDto, onOpenTests: (() -> Unit)?) {
    // One scrolling surface. Previously the header sat in a fixed Column and only the activity
    // list scrolled, inside `weight(1f)` -- so on a short screen, or at a large font scale,
    // the history was confined to a few dozen pixels while the summary above it could not be
    // scrolled out of the way.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            ScreenHeader(
                title = "Your progress",
                subtitle = "Tests, interviews and roadmap completion.",
                illustration = {
                    GrowthIllustration(
                        modifier = Modifier.size(64.dp),
                        track = MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f),
                        accent = MaterialTheme.colorScheme.secondary,
                    )
                },
            )
        }

        item { PerformanceCard(dashboard = dashboard, onOpenTests = onOpenTests) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                StatChip(
                    label = "Tests taken",
                    value = dashboard.completedAssessments.toString(),
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = "Interviews",
                    value = dashboard.completedInterviews.toString(),
                    icon = Icons.Filled.RecordVoiceOver,
                    tint = accentCyanColor(),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = "Roadmap tasks",
                    value = "${dashboard.roadmapTasksDone}/${dashboard.roadmapTasksTotal}",
                    icon = Icons.Filled.Checklist,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (dashboard.topWeaknesses.isNotEmpty()) {
            item { SectionHeader(title = "Focus areas") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // Purely informational tags, not actions -- a chip component with an empty
                    // onClick left these focusable but inert for keyboard/TalkBack users.
                    dashboard.topWeaknesses.take(3).forEach { skill ->
                        StatusPill(label = skill, color = warningColor())
                    }
                }
            }
        }

        item { SectionHeader(title = "Recent activity") }

        if (dashboard.trend.isEmpty()) {
            item { NoActivityYet(onOpenTests = onOpenTests) }
        } else {
            items(dashboard.trend.reversed(), key = { it.createdAt + it.label }) { point ->
                TrendCard(point)
            }
        }
    }
}

/**
 * The one figure on this screen that is a judgement rather than a count.
 *
 * When it exists it gets the ring and the hero treatment, because it is what a student opens
 * this screen to see. When it does not, the card says so in words and offers the action that
 * would produce it -- rather than a ring drawn at 0%, which would be a claim that they scored
 * nothing rather than an admission that nothing has been measured.
 */
@Composable
private fun PerformanceCard(dashboard: ProgressDashboardDto, onOpenTests: (() -> Unit)?) {
    if (dashboard.performanceScore != null) {
        HeroCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    // Names the formula: the mean of the latest test and interview score.
                    text = "Test & interview performance",
                    style = MaterialTheme.typography.titleSmall,
                )
                ScoreRing(
                    percent = (dashboard.performanceScore * 100).roundToInt(),
                    ringColor = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                )
            }
        }
    } else {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Test & interview performance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Complete a test or interview and your score appears here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                GrowthIllustration(
                    modifier = Modifier.size(72.dp),
                    track = MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f),
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }
            if (onOpenTests != null) {
                PrimaryButton(
                    text = "Take a test",
                    onClick = onOpenTests,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun NoActivityYet(onOpenTests: (() -> Unit)?) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Nothing here yet",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Take a test or interview to start building your history.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        if (onOpenTests != null) {
            SecondaryButton(
                text = "Go to Test Center",
                onClick = onOpenTests,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            )
        }
    }
}

@Composable
private fun TrendCard(point: ProgressTrendPointDto) {
    val isInterview = point.kind == "interview"
    val percent = (point.score * 100).roundToInt()
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconChip(
                icon = if (isInterview) Icons.Filled.RecordVoiceOver else Icons.AutoMirrored.Filled.Assignment,
                tint = if (isInterview) accentCyanColor() else MaterialTheme.colorScheme.secondary,
                size = 36.dp,
            )
            Spacer(Modifier.size(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = point.createdAt.substringBefore("T"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(
                label = "$percent%",
                color = when {
                    percent >= 75 -> successColor()
                    percent >= 50 -> warningColor()
                    else -> MaterialTheme.colorScheme.error
                },
            )
        }
    }
}
