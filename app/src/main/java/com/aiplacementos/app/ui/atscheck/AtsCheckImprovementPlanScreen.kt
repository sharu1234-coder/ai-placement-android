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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.AtsCheckReportDto
import com.aiplacementos.app.data.remote.AtsRecommendationDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.StatusPill
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun AtsCheckImprovementPlanScreen(checkId: Int, onOpenResumeUpload: () -> Unit, onBack: () -> Unit) {
    val network = networkModule()
    val viewModel: AtsCheckImprovementPlanViewModel = viewModel(
        factory = viewModelFactory { initializer { AtsCheckImprovementPlanViewModel(checkId, network.atsCheckRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { DetailTopBar(title = "Improve Your Resume", onBack = onBack) }) { padding ->
        when (val current = state) {
            is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            is UiState.Error -> ErrorState(
                message = current.message,
                onRetry = viewModel::load,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Empty -> EmptyState(
                message = "No recommendations -- your resume is in great shape!",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Success<AtsCheckReportDto> -> ImprovementPlanContent(
                report = current.data,
                onOpenResumeUpload = onOpenResumeUpload,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ImprovementPlanContent(report: AtsCheckReportDto, onOpenResumeUpload: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Text(
                text = "Follow this roadmap to improve your ATS score and resume quality.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (report.potentialScore != null) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        text = "Estimated Potential: ${report.potentialScore}/100",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    if (report.potentialScoreExplanation != null) {
                        Text(
                            text = report.potentialScoreExplanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
        }
        items(report.recommendations, key = { it.rank }) { recommendation ->
            RecommendationRow(recommendation = recommendation, onFixThis = onOpenResumeUpload)
        }
    }
}

@Composable
private fun RecommendationRow(recommendation: AtsRecommendationDto, onFixThis: () -> Unit) {
    val clickable = recommendation.fixTarget == "resume_upload"
    AppCard(
        modifier = Modifier.fillMaxWidth().let { if (clickable) it.clickable(onClick = onFixThis) else it },
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${recommendation.rank}.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.padding(start = Spacing.sm).weight(1f)) {
                Text(text = recommendation.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                StatusPill(
                    label = if (recommendation.priority == "high") {
                        "High Priority"
                    } else {
                        "${recommendation.priority.replaceFirstChar { it.uppercase() }} Impact"
                    },
                    color = severityColor(recommendation.priority),
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = recommendation.rationale,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            if (clickable) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
