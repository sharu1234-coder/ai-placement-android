package com.aiplacementos.app.ui.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.aiplacementos.app.data.remote.WeaknessRecordDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.theme.Spacing

/** Pure, stateless presentation of the persisted cross-interview weakness view -- same
 * convention as NextBestActionCard/TodaysMissionCard. Renders every record it's given; the
 * caller (WeaknessMemorySection) decides whether to show the card at all. */
@Composable
fun WeaknessMemoryCard(records: List<WeaknessRecordDto>, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(text = "Recurring Patterns", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            records.forEach { record -> WeaknessMemoryRow(record) }
        }
    }
}

@Composable
private fun WeaknessMemoryRow(record: WeaknessRecordDto) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = dimensionLabel(record.skillKey), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = statusLabel(record),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun dimensionLabel(skillKey: String): String =
    skillKey.removePrefix("interview:").removePrefix("recruiter_attack:")
        .replace("_", " ").replaceFirstChar { it.uppercase() }

private fun statusLabel(record: WeaknessRecordDto): String = when (record.status) {
    "NEW" -> "Flagged"
    "REPEATED" -> "Flagged ${record.occurrences}x"
    "IMPROVING" -> "Improving"
    "NEARLY_CLOSED" -> "Nearly resolved"
    "CLOSED" -> "Resolved"
    else -> record.status
}
