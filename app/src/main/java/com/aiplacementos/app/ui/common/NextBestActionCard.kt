package com.aiplacementos.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiplacementos.app.data.remote.RecommendedActionDto
import com.aiplacementos.app.ui.theme.Spacing

/**
 * Pure, stateless presentation of a RecommendedActionDto -- deliberately data-in, no ViewModel or
 * network access of its own, same convention as ScoreRing/SkillBar, so any screen (Career
 * Twin now, Home later) can embed it by supplying its own fetched action. [onStart] is
 * optional so a screen that doesn't yet have a "start" flow to hand off to (e.g. no mission
 * screen exists before Today's Mission ships) can omit the button entirely rather than wiring
 * a no-op.
 */
@Composable
fun NextBestActionCard(action: RecommendedActionDto, modifier: Modifier = Modifier, onStart: (() -> Unit)? = null) {
    // An AccentCard rather than a plain one: this is the single recommendation the product
    // treats as authoritative, and on a page of equal-weight cards it previously read as one
    // more section. The rail and tint lift it without spending the screen's one hero.
    AccentCard(
        modifier = modifier.fillMaxWidth(),
        accent = MaterialTheme.colorScheme.secondary,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(icon = Icons.Filled.Lightbulb, tint = MaterialTheme.colorScheme.secondary, size = 36.dp)
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = "Your Next Move",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = action.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        action.estimatedMinutes?.let { minutes ->
            Text(
                text = "Estimated: $minutes min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
            )
        }
        if (action.reasons.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "WHY?",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                action.reasons.forEach { reason ->
                    Text(
                        text = "✓ $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        if (onStart != null && action.hasRecommendation) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            PrimaryButton(text = "Start", onClick = onStart, modifier = Modifier.fillMaxWidth())
        }
    }
}
