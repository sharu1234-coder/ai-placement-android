package com.aiplacementos.app.ui.mission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.aiplacementos.app.data.remote.MissionDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.theme.Spacing

/** Pure, stateless presentation of a MissionDto -- same convention as NextBestActionCard: no
 * ViewModel or network access of its own. Deliberately renders a single action (title +
 * estimated minutes + why), not a fabricated multi-step checklist -- the backend's Mission is
 * genuinely one recommendation snapshotted for the day, and inventing sub-steps that don't
 * exist in the data would violate the no-fake-data rule. */
@Composable
fun TodaysMissionCard(
    mission: MissionDto,
    modifier: Modifier = Modifier,
    onStart: () -> Unit = {},
    onComplete: () -> Unit = {},
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(text = "Today's Mission", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(text = mission.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        mission.estimatedMinutes?.let { minutes ->
            Text(
                text = "Estimated: $minutes min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (mission.reasons.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(text = "WHY?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                mission.reasons.forEach { reason ->
                    Text(text = "✓ $reason", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        when (mission.status) {
            "not_started" -> PrimaryButton(text = "Start Mission", onClick = onStart, modifier = Modifier.fillMaxWidth())
            "in_progress" -> PrimaryButton(text = "Mark Complete", onClick = onComplete, modifier = Modifier.fillMaxWidth())
            "completed" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(text = "Completed today", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
