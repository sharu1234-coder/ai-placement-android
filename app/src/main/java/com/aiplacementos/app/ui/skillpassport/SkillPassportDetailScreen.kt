package com.aiplacementos.app.ui.skillpassport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.WifiOff
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
import com.aiplacementos.app.data.remote.EvidenceItemDto
import com.aiplacementos.app.data.remote.SkillPassportItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.ExpandableSection
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

private fun stateDisplay(state: String): Pair<String, Boolean> = when (state) {
    "verified" -> "Verified" to false
    "strong" -> "Strong" to false
    "supported" -> "Developing" to true
    "stale" -> "Stale" to true
    else -> "Gap" to true // "claimed"
}

@Composable
fun SkillPassportDetailScreen(skill: String, onBack: () -> Unit) {
    val network = networkModule()
    val viewModel: SkillPassportDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SkillPassportDetailViewModel(network.skillPassportRepository, skill) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { DetailTopBar(title = skill, onBack = onBack) }) { padding ->
        when (val current = state) {
            is SkillPassportDetailUiState.Loading ->
                SkeletonList(count = 3, modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md))
            is SkillPassportDetailUiState.Error -> ErrorState(
                message = current.message,
                icon = if (current.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
                modifier = Modifier.fillMaxSize().padding(padding),
                onRetry = viewModel::load,
            )
            is SkillPassportDetailUiState.Success ->
                SkillPassportDetailContent(item = current.item, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun SkillPassportDetailContent(item: SkillPassportItemDto, modifier: Modifier = Modifier) {
    val (label, _) = stateDisplay(item.state)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Text(text = item.skill, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.level ?: "Not yet demonstrated",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = "${item.confidencePercent}% confidence",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(text = "Status: $label", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            AppCard {
                if (item.evidence.isEmpty()) {
                    Text(
                        text = "No evidence yet. This skill hasn't been demonstrated through an " +
                            "assessment, project, interview, or resume match -- nothing here is fabricated.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    ExpandableSection(title = "Why is this $label?", initiallyExpanded = true) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            item.evidence.forEach { evidence -> EvidenceRow(evidence) }
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(text = "Next Best Action", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(text = item.nextActionHint, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EvidenceRow(evidence: EvidenceItemDto) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp, end = Spacing.sm),
        )
        Column {
            Text(text = evidence.label, style = MaterialTheme.typography.bodyMedium)
            evidence.detectedAt?.let { detectedAt ->
                Text(
                    text = detectedAt.take(10),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
