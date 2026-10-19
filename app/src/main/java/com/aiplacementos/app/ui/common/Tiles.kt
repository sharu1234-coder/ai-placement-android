package com.aiplacementos.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.ui.theme.Elevation
import com.aiplacementos.app.ui.theme.Spacing

/**
 * The shared vocabulary for "a number", "a thing you can open" and "a state".
 *
 * Each of these existed three or four times over as a private composable inside a feature
 * screen -- `StatCard` in Test Center, `StatTile` in Progress, `ScorePill` in Test Center,
 * `PriorityBadge` in Placement Path -- each drawn slightly differently. Consolidating them is
 * what makes the screens look like one product rather than one per author, and it is also the
 * cheapest way to improve every screen at once: a change here lands everywhere.
 */

/**
 * One measured figure.
 *
 * [value] is passed already formatted, including the honest unavailable form. The app's
 * convention for "we have not measured this" is the string `--`, and this component renders
 * that in the muted colour rather than the emphatic one, so a screen full of unavailable
 * metrics does not read as a screen full of results.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.secondary,
) {
    val hasValue = value.isNotBlank() && value.trimStart('-').isNotBlank()
    AppCard(modifier = modifier, elevation = Elevation.level0) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // One announcement, so a screen reader says what the number measures instead
                // of reading a bare figure followed by an unrelated word.
                .clearAndSetSemantics { contentDescription = "$label: $value" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (icon != null) {
                IconChip(icon = icon, tint = tint, size = 36.dp)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (hasValue) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Something the student can open: an icon on its own ground, a name, optional context, and a
 * chevron that says it leads somewhere.
 *
 * The chevron is not decoration. The flat coloured rectangles this replaces gave no signal at
 * all that they were tappable, which is the kind of thing students report as "the app did
 * nothing when I pressed it".
 *
 * [busy] replaces the chevron with a spinner and stops the click, so a tile that is starting
 * something slow -- a test blueprint being fetched -- cannot be fired twice.
 */
@Composable
fun FeatureTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: String? = null,
    tint: Color = MaterialTheme.colorScheme.secondary,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    AppCard(
        modifier = modifier.clickable(enabled = enabled && !busy, onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconChip(icon = icon, tint = tint)
            Spacer(Modifier.size(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (meta != null) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (busy) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = tint,
                )
            } else if (enabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * A short state, as a word on a tinted ground.
 *
 * The word is the information; the tint only reinforces it. That ordering is deliberate and is
 * why this takes a [label] rather than deriving one from the colour -- the app has a standing
 * rule that nothing is communicated by colour alone.
 */
@Composable
fun StatusPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
        )
    }
}

/**
 * A labelled bar: name on the left, value on the right, a track underneath.
 *
 * [percent] null renders no track at all rather than an empty one. An empty progress bar and a
 * zero-length one look identical, and the app treats "not measured" and "measured at zero" as
 * different facts about a student -- so the bar is simply absent when there is nothing to plot.
 */
@Composable
fun MetricBar(
    label: String,
    valueText: String,
    percent: Int?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
    trailing: String? = null,
    trailingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (percent != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (percent != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(6.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = color.copy(alpha = 0.16f),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                        .height(6.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = color,
                ) {}
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = trailingColor,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}
