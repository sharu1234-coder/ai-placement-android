package com.aiplacementos.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.ui.theme.Elevation
import com.aiplacementos.app.ui.theme.Spacing

/**
 * The shapes that repeat across the product, in the arrangement the approved reference uses.
 *
 * These are here rather than in [Surfaces] because they are *compositions* -- a header, a
 * filter row, a figure-and-label chip -- assembled from the surfaces in that file. Keeping the
 * two layers apart is what stops a screen reaching for a raw `Card` when it means "a stat".
 *
 * Every one of them was a private copy in two or more feature packages before this file
 * existed, which is the whole reason the app read as several products stitched together.
 */

/**
 * The top of a feature screen: what this is, what it is for, and a small figure to the right.
 *
 * The subtitle is not decoration. Every screen in the reference carries one, and the reason is
 * that the titles here are nouns -- "Test Center", "Your progress" -- which name a place
 * rather than explain what a student can do there.
 *
 * [illustration] is a slot rather than a parameter so a caller supplies its own figure at its
 * own size, and so a screen with nothing worth drawing simply omits it instead of being given
 * a generic placeholder.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    illustration: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
        if (illustration != null) {
            Spacer(Modifier.size(Spacing.sm))
            illustration()
        }
    }
}

/**
 * A single-choice filter row.
 *
 * `LazyRow`, not `Row`, deliberately: the longest set in the app is five options and at a
 * raised font scale they do not fit a 320dp screen. A wrapping row would push the content
 * below it down by a whole line on the narrow case only; scrolling keeps the row one line
 * high at every width.
 *
 * [Spacing.chipGap] rather than `sm` -- the app has one measured gap for chip rows, and the
 * arithmetic for why 12dp is the floor is recorded on that token.
 */
@Composable
fun FilterChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
    ) {
        items(options) { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option) },
                shape = MaterialTheme.shapes.extraLarge,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                ),
            )
        }
    }
}

/**
 * A figure with its icon beside it rather than above it.
 *
 * The difference from [StatTile] is the axis, and it matters: three of these fit a 320dp row
 * because the label wraps beside the icon instead of under a centred column. [StatTile] stays
 * for the cases where the figure is the only thing on the card.
 *
 * [value] arrives already formatted, including the app's `--` for a figure that has not been
 * measured. That form renders muted, so a screen of unavailable metrics does not read as a
 * screen of results.
 */
@Composable
fun StatChip(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.secondary,
) {
    val measured = value.isNotBlank() && value.trimStart('-').isNotBlank()
    AppCard(modifier = modifier, elevation = Elevation.level0) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconChip(icon = icon, tint = tint, size = 32.dp)
            Spacer(Modifier.size(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (measured) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One promise an empty state makes. Grouped three-up by [BenefitRow]. */
data class Benefit(val icon: ImageVector, val label: String)

/**
 * What a student gets if they take the action an empty state is offering.
 *
 * Added because the empty screens stated a *fact* -- "No roadmap yet" -- and then asked for a
 * tap without ever saying what the tap produces. Three short promises is the most that can be
 * read at a glance, so the row takes exactly three and the type wraps to two lines rather
 * than truncating.
 */
@Composable
fun BenefitRow(
    benefits: List<Benefit>,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.secondary,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        benefits.take(3).forEach { benefit ->
            AppCard(modifier = Modifier.weight(1f), elevation = Elevation.level0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) { contentDescription = benefit.label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconChip(icon = benefit.icon, tint = tint, size = 34.dp)
                    Text(
                        text = benefit.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
    }
}
