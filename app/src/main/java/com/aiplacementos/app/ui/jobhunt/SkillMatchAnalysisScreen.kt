package com.aiplacementos.app.ui.jobhunt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.SkillMatchRowDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

/**
 * "See exactly where you stand" — one row per skill the advert asks for.
 *
 * Every row states three things and invents none of them: the skill, how it reads to a
 * recruiter (the badge), and where our confidence comes from (the caption). The server decides
 * both classifications; this screen only lays them out.
 *
 * Free, deliberately. The diagnosis is not what Premium sells — the readiness breakdown and
 * the before-you-apply plan are. A student who cannot see their own gaps has no reason to
 * believe the product understands them, and no reason to pay for the part that acts on it.
 */
@Composable
fun SkillMatchAnalysisScreen(
    listingId: Int,
    onBack: () -> Unit,
    onOpenRoadmap: () -> Unit,
    onOpenReadiness: () -> Unit,
) {
    val network = networkModule()
    val viewModel: SkillMatchAnalysisViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SkillMatchAnalysisViewModel(network.jobHuntRepository, listingId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { DetailTopBar(title = "Skill Match Analysis", onBack = onBack) }) { padding ->
        when (val current = state) {
            is SkillMatchUiState.Loading ->
                SkeletonList(count = 5, modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md))

            is SkillMatchUiState.Failed -> FailureState(
                failure = current.failure,
                onRetry = viewModel::retry,
                onOpenSetup = viewModel::retry,
                onOpenSubscriptions = viewModel::retry,
            )

            is SkillMatchUiState.Success -> AnalysisContent(
                analysis = current.analysis,
                onOpenRoadmap = onOpenRoadmap,
                onOpenReadiness = onOpenReadiness,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun AnalysisContent(
    analysis: com.aiplacementos.app.data.remote.SkillMatchAnalysisDto,
    onOpenRoadmap: () -> Unit,
    onOpenReadiness: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${analysis.matchPercent()}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Overall Match",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.xs),
                )
            }
            LinearProgressIndicator(
                progress = { analysis.matchPercent() / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm)
                    // The number above is already announced; a second reading of the same
                    // value as a progress bar is noise for a screen-reader user.
                    .clearAndSetSemantics {},
            )
            if (analysis.verifiedSkillCount > 0) {
                Text(
                    text = "${analysis.verifiedSkillCount} of these are backed by verified evidence",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        if (analysis.rows.isEmpty()) {
            // Not an error, and not "you match nothing". The advert named no skills we
            // recognise, which is a fact about the advert.
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "This listing doesn't name specific skills",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "There's nothing here to check you against. The match score is " +
                        "based on the other things we could compare.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        } else {
            sortedAnalysisRows(analysis.rows).forEach { row -> SkillRow(row) }
        }

        if (analysis.biggestGapSkill != null) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Close your biggest gap",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "See exactly how to boost your match score for " +
                        "${analysis.biggestGapSkill}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                PrimaryButton(
                    text = "Go to Roadmap",
                    onClick = onOpenRoadmap,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                )
            }
        }

        PrimaryButton(
            text = "View Application Readiness",
            onClick = onOpenReadiness,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SkillRow(row: SkillMatchRowDto) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = strengthIcon(row.strength),
                // The badge to the right already names the state; announcing the icon too
                // would say it twice.
                contentDescription = null,
                tint = strengthTint(row.strength),
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
                Text(
                    text = row.skill,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                // Prefers the server's own sentence, which is specific ("Last confirmed over
                // three months ago"), and falls back to the short caption so a row is never
                // left as a bare skill name.
                val caption = row.detail ?: skillProvenanceCaption(row.status)
                if (caption.isNotBlank()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val label = matchStrengthLabel(row.strength)
            if (label.isNotBlank()) {
                Surface(
                    color = strengthContainer(row.strength),
                    shape = RoundedCornerShape(Spacing.sm),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = strengthTint(row.strength),
                        modifier = Modifier
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                            .semantics {
                                contentDescription = "${row.skill}: $label"
                            },
                    )
                }
            }
        }
    }
}

// Status is never carried by colour alone: each badge has its own icon as well as its own
// tint, so the four states stay distinguishable in greyscale and to a colour-blind reader.
@Composable
private fun strengthIcon(strength: String): ImageVector = when (strength) {
    "strong_match" -> Icons.Filled.Verified
    "match" -> Icons.Outlined.Description
    "skill_gap" -> Icons.Filled.RadioButtonUnchecked
    else -> Icons.Filled.HelpOutline
}

@Composable
private fun strengthTint(strength: String): Color = when (strength) {
    "strong_match" -> MaterialTheme.colorScheme.primary
    "skill_gap" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun strengthContainer(strength: String): Color = when (strength) {
    "strong_match" -> MaterialTheme.colorScheme.primaryContainer
    "skill_gap" -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}
