package com.aiplacementos.app.ui.atscheck

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.AtsCheckReportDto
import com.aiplacementos.app.data.remote.AtsIssueDto
import com.aiplacementos.app.data.remote.CategoryBreakdownDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.IconChip
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.SkeletonCircle
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.SkeletonText
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun AtsCheckResultScreen(
    checkId: Int,
    onBack: () -> Unit,
    onOpenCategory: (category: String) -> Unit,
    onOpenSectionAnalysis: () -> Unit,
    onOpenIssues: () -> Unit,
    onOpenImprovementPlan: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenParsedView: () -> Unit,
) {
    val network = networkModule()
    val viewModel: AtsCheckResultViewModel = viewModel(
        factory = viewModelFactory { initializer { AtsCheckResultViewModel(checkId, network.atsCheckRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Matches every other drill-into-item screen reached from a history list (e.g.
    // AtsCheckIssuesScreen, SkillPassportDetailScreen) -- this was the one result screen in
    // that family without a back arrow. The share action moves from an inline header Row into
    // the top bar's actions slot so it survives alongside the new back arrow.
    Scaffold(
        topBar = {
            DetailTopBar(
                title = "ATS Check Result",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        val currentState = state
                        val shareText = if (currentState is UiState.Success<AtsCheckReportDto>) {
                            "My resume scored ${currentState.data.overallScore}/100 on TALVIORA AI's Resume ATS Check."
                        } else {
                            "Check out TALVIORA AI's Resume ATS Check."
                        }
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                },
                                null,
                            ),
                        )
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            is UiState.Loading -> AtsResultLoadingSkeleton(modifier = Modifier.padding(padding))
            is UiState.Error -> ErrorState(
                message = current.message,
                onRetry = viewModel::load,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Empty -> ErrorState(
                message = "This report is no longer available.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Success<AtsCheckReportDto> -> ResultContent(
                report = current.data,
                onOpenCategory = onOpenCategory,
                onOpenSectionAnalysis = onOpenSectionAnalysis,
                onOpenIssues = onOpenIssues,
                onOpenImprovementPlan = onOpenImprovementPlan,
                onOpenHistory = onOpenHistory,
                onOpenParsedView = onOpenParsedView,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/** Silhouettes the score header + issue/recommendation rows this screen renders once loaded,
 * instead of a bare spinner unrelated to what's about to appear. */
@Composable
private fun AtsResultLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                SkeletonCircle(size = 72.dp)
                Spacer(modifier = Modifier.height(Spacing.sm))
                SkeletonText(widthFractions = listOf(0.4f))
            }
        }
        SkeletonList(count = 3)
    }
}

@Composable
private fun ResultContent(
    report: AtsCheckReportDto,
    onOpenCategory: (category: String) -> Unit,
    onOpenSectionAnalysis: () -> Unit,
    onOpenIssues: () -> Unit,
    onOpenImprovementPlan: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenParsedView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val highPriorityIssues = report.issues.filter { it.severity == "high" }.take(3)
    // "View Full Report" scrolls to the "Explore Your Report" menu header rather than
    // pushing a second destination -- this whole screen is one long dashboard (matches
    // the reference design's crop being just the top of a longer scrollable screen).
    // Index computed, not hardcoded: the priority-issues card above it is conditional.
    val exploreHeaderIndex = 3 + (if (highPriorityIssues.isNotEmpty()) 1 else 0)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { ScoreSummaryCard(report) }

        item {
            Text(
                text = "View Full Report →",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    scope.launch { listState.animateScrollToItem(exploreHeaderIndex) }
                },
            )
        }

        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(72.dp * (report.categoryBreakdown.size / 2 + 1)),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                userScrollEnabled = false,
            ) {
                gridItems(report.categoryBreakdown) { category ->
                    CategoryStatCard(category = category, onClick = { onOpenCategory(category.category) })
                }
            }
        }

        if (highPriorityIssues.isNotEmpty()) {
            item { TopPriorityIssuesCard(issues = highPriorityIssues, totalHigh = report.issues.count { it.severity == "high" }, onViewAll = onOpenIssues) }
        }

        item {
            Text(
                text = "Explore Your Report",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        item {
            ReportMenuRow(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                title = "Section Analysis",
                subtitle = "See which sections are complete or need work",
                onClick = onOpenSectionAnalysis,
            )
        }
        item {
            ReportMenuRow(
                icon = Icons.Filled.WorkOutline,
                title = "Improve Your Resume",
                subtitle = "Your prioritized improvement roadmap",
                onClick = onOpenImprovementPlan,
            )
        }
        item {
            ReportMenuRow(
                icon = Icons.Filled.History,
                title = "Score History",
                subtitle = "Track your ATS score over time",
                onClick = onOpenHistory,
            )
        }
        item {
            ReportMenuRow(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "How ATS Reads Your Resume",
                subtitle = "See what an ATS actually extracts from your file",
                onClick = onOpenParsedView,
            )
        }
    }
}

@Composable
private fun ScoreSummaryCard(report: AtsCheckReportDto) {
    AppCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text = "ATS Readiness Score",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
            ScoreRing(
                percent = report.overallScore,
                displayText = "${report.overallScore}/100",
                size = 96.dp,
                ringColor = scoreBandColor(report.scoreCategory),
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
            )
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = scoreBandColor(report.scoreCategory), modifier = Modifier.size(18.dp))
                    Text(
                        text = scoreBandLabel(report.scoreCategory).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
                Text(
                    text = report.aiSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun CategoryStatCard(category: CategoryBreakdownDto, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(text = atsCategoryLabel(category.category), style = MaterialTheme.typography.labelMedium)
        Text(
            text = "${category.score}%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = scoreBandColor(scoreCategoryFor(category.score)),
        )
    }
}

private fun scoreCategoryFor(score: Int): String = when {
    score >= 90 -> "excellent"
    score >= 80 -> "very_good"
    score >= 70 -> "good"
    score >= 60 -> "needs_improvement"
    else -> "needs_attention"
}

@Composable
private fun TopPriorityIssuesCard(issues: List<AtsIssueDto>, totalHigh: Int, onViewAll: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(text = "Top Priority Issues", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "$totalHigh high priority issue${if (totalHigh == 1) "" else "s"} found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onViewAll) {
                Text(text = "View All", style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(modifier = Modifier.padding(top = Spacing.sm)) {
            issues.forEach { issue ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = severityColor(issue.severity),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(text = issue.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
    }
}

@Composable
private fun ReportMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconChip(icon = icon, tint = MaterialTheme.colorScheme.secondary, size = 38.dp)
            Column(modifier = Modifier.padding(start = Spacing.sm).weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
