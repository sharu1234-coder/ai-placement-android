package com.aiplacementos.app.ui.jobhunt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ApplicationReadinessDto
import com.aiplacementos.app.data.remote.ReadinessDimensionDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.SkillBar
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

/**
 * "Am I ready to apply to this job?" — five dimensions, and an honest gap.
 *
 * The screen's whole value is that it is checkable. Every bar carries the sentence explaining
 * where its number came from, and a dimension the server could not judge shows "Not checked
 * yet" rather than an empty bar at 0% — a student with no mock interviews has not failed
 * interviews, and telling them they are 0% ready is both false and discouraging in exactly the
 * place they are most likely to stop.
 *
 * Premium, enforced server-side. The paywall is a distinct state from an error for the same
 * reason: a price is not a failure, and rendering them the same way makes one look like the
 * other in both directions.
 */
@Composable
fun ApplicationReadinessScreen(
    listingId: Int,
    onBack: () -> Unit,
    onFixGap: (ReadinessGapDestination) -> Unit,
    onOpenSubscriptions: () -> Unit,
) {
    val network = networkModule()
    val viewModel: ApplicationReadinessViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ApplicationReadinessViewModel(network.jobHuntRepository, listingId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { DetailTopBar(title = "Application Readiness", onBack = onBack) },
    ) { padding ->
        when (val current = state) {
            is ReadinessUiState.Loading ->
                SkeletonList(
                    count = 5,
                    modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
                )

            is ReadinessUiState.PaywallRequired -> PaywallState(
                message = current.message,
                onOpenSubscriptions = onOpenSubscriptions,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            is ReadinessUiState.Failed -> FailureState(
                failure = current.failure,
                onRetry = viewModel::retry,
                onOpenSetup = viewModel::retry,
                onOpenSubscriptions = onOpenSubscriptions,
            )

            is ReadinessUiState.Success -> ReadinessContent(
                readiness = current.readiness,
                onFixGap = onFixGap,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun PaywallState(
    message: String,
    onOpenSubscriptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.WorkspacePremium,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Application Readiness is Premium",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        // The server's own sentence, which names what stays free. More useful than a generic
        // upsell, because it tells the student what they are not losing.
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(
            text = "See Premium",
            onClick = onOpenSubscriptions,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReadinessContent(
    readiness: ApplicationReadinessDto,
    onFixGap: (ReadinessGapDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val percent = readiness.overallPercent()
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (percent != null) {
                ScoreRing(percent = percent, label = readinessBandLabel(readiness.band))
            } else {
                // Nothing at all could be judged. A 0% ring here would be a confident-looking
                // claim built on no evidence.
                Text(
                    text = "Not enough information yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Add a resume, your skills or an experience level and this fills in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        readiness.dimensions.forEach { dimension -> DimensionRow(dimension) }

        if (readiness.unknownDimensions.isNotEmpty()) {
            // Named, not hidden. Four bars where a student expects five reads as a bug; saying
            // which one we skipped and why reads as honesty.
            Row(modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "We couldn't check: ${readiness.unknownDimensions.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }

        val gap = readiness.biggestGap
        if (gap != null) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Before applying",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = gap.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .semantics { heading() },
                )
                Text(
                    text = gap.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                PrimaryButton(
                    text = "Fix This Gap",
                    onClick = { onFixGap(gapDestination(gap.action)) },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                )
            }
        }

        if (readiness.tips.isNotEmpty()) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Application tips",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                readiness.tips.forEach { tip ->
                    Row(
                        modifier = Modifier.padding(top = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DimensionRow(dimension: ReadinessDimensionDto) {
    val percent = dimensionPercent(dimension.score)
    Column(modifier = Modifier.fillMaxWidth()) {
        if (percent != null) {
            SkillBar(label = dimension.label, percent = percent)
        } else {
            // No bar at all rather than an empty one. An empty bar is read as zero, and zero
            // is a claim we have no basis for.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = dimension.label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = unjudgedDimensionLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Always shown: this is what makes the number checkable rather than asserted.
        Text(
            text = dimension.detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
