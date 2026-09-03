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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    AppCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(text = "Your Next Move", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(text = action.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        action.estimatedMinutes?.let { minutes ->
            Text(
                text = "Estimated: $minutes min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (action.reasons.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(text = "WHY?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                action.reasons.forEach { reason ->
                    Text(text = "✓ $reason", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (onStart != null && action.hasRecommendation) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            PrimaryButton(text = "Start", onClick = onStart, modifier = Modifier.fillMaxWidth())
        }
    }
}
