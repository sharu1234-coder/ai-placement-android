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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor

private fun stateDisplay(state: String): Pair<String, Boolean> = when (state) {
    "verified" -> "Verified" to false
    "strong" -> "Strong" to false
    "supported" -> "Developing" to true
    "stale" -> "Stale" to true
    else -> "Gap" to true // "claimed"
}

/** "Today" when the ISO timestamp's date matches the device's current date, else the bare
 * date -- same lightweight, no-new-dependency spirit as EvidenceRow's existing detectedAt
 * handling below (java.time is already used elsewhere in this module's package). */
private fun formatLastVerified(isoTimestamp: String): String {
    val datePart = isoTimestamp.take(10)
    val today = java.time.LocalDate.now().toString()
    return if (datePart == today) "Today" else datePart
}

private fun evidenceIcon(source: String): ImageVector = when (source) {
    "assessment" -> Icons.AutoMirrored.Filled.Assignment
    "practice" -> Icons.Filled.FitnessCenter
    "project" -> Icons.Filled.Construction
    "interview" -> Icons.Filled.Mic
    "resume" -> Icons.Filled.Description
    else -> Icons.Filled.CheckCircle
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
    val (label, isGap) = stateDisplay(item.state)

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
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(label = label, isGap = isGap)
                    item.lastVerifiedAt?.let { lastVerifiedAt ->
                        Text(
                            text = "Last verified: ${formatLastVerified(lastVerifiedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
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

/** A colored pill, not color alone -- the label text itself already carries the status, this
 * just gives it the same "premium status chip" treatment the reference design uses instead of
 * plain inline text. */
@Composable
private fun StatusBadge(label: String, isGap: Boolean) {
    val color = if (isGap) warningColor() else successColor()
    Surface(color = color.copy(alpha = 0.15f), contentColor = color, shape = MaterialTheme.shapes.small) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
        )
    }
}

@Composable
private fun EvidenceRow(evidence: EvidenceItemDto) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = evidenceIcon(evidence.source),
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
