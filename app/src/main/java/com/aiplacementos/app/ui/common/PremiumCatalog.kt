package com.aiplacementos.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.ui.theme.AiPlacementOsTheme
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.accentCyanColor
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor

/**
 * The surfaces, tiles and illustrations introduced by the premium redesign, on one screen.
 *
 * Separate from [ComponentCatalog] rather than appended to it, for the reason that file gives
 * for being separate from the token catalog: that one previews the controls a form is built
 * from, this one previews the *composition* pieces a screen is built from. Keeping them apart
 * means neither preview grows into a 3000dp scroll nobody opens.
 *
 * ## What to look at
 *
 * - [HeroCard] against [AppCard] against [AccentCard]: three weights, and whether the page
 *   still reads as having one most-important thing on it.
 * - The large-font preview: [StatTile]'s value and label at 1.5x, and whether [FeatureTile]'s
 *   two-line subtitle still fits beside its chevron.
 * - The 320dp preview: three stat tiles across is the tightest row in the app.
 * - The illustrations at a small size, where thin strokes are the first thing to break.
 */
@Composable
private fun PremiumBody() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionHeader(
                title = "Where you stand",
                eyebrow = "Section header",
                supporting = "Eyebrow, title and supporting line.",
            )

            HeroCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "YOUR TARGET", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = "Business Analyst",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = "Application: not linked", style = MaterialTheme.typography.bodyMedium)
            }

            AccentCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Your Next Move",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "Take the SQL diagnostic.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile(
                    label = "Average score",
                    // The honest unavailable form, which should read as muted rather than
                    // emphatic -- this preview is the only place both appear side by side.
                    value = "--",
                    icon = Icons.Filled.EmojiEvents,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Tests completed",
                    value = "12",
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Accuracy",
                    value = "68%",
                    icon = Icons.Filled.TrackChanges,
                    tint = accentCyanColor(),
                    modifier = Modifier.weight(1f),
                )
            }

            FeatureTile(
                title = "SQL",
                subtitle = "Databases & queries",
                icon = Icons.Filled.Storage,
                tint = accentCyanColor(),
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
            FeatureTile(
                title = "Programming",
                subtitle = "Coding & problem solving",
                icon = Icons.Filled.Code,
                tint = MaterialTheme.colorScheme.primary,
                onClick = {},
                busy = true,
                modifier = Modifier.fillMaxWidth(),
            )

            AppCard(modifier = Modifier.fillMaxWidth()) {
                MetricBar(label = "Evidence Coverage", valueText = "11%", percent = 11)
                MetricBar(
                    label = "Preparation",
                    // percent = null is the case worth checking: no track at all, because an
                    // empty bar and a zero-length bar look the same and mean different things.
                    valueText = "Not started yet",
                    percent = null,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusPill(label = "Good · 82%", color = successColor())
                StatusPill(label = "Average · 61%", color = warningColor())
                StatusPill(label = "HIGH", color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                RouteIllustration(
                    modifier = Modifier.size(width = 140.dp, height = 100.dp),
                    track = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                    line = MaterialTheme.colorScheme.secondary,
                    accent = MaterialTheme.colorScheme.tertiary,
                    ground = MaterialTheme.colorScheme.background,
                )
                TargetIllustration(
                    modifier = Modifier.size(100.dp),
                    ring = MaterialTheme.colorScheme.primary,
                    accent = MaterialTheme.colorScheme.tertiary,
                )
                GrowthIllustration(
                    modifier = Modifier.size(100.dp),
                    track = MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f),
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }

            BrandMark()
        }
    }
}

@Preview(name = "Premium surfaces", showBackground = true, heightDp = 1500)
@Composable
private fun PremiumCatalogPreview() {
    AiPlacementOsTheme { PremiumBody() }
}

/** The accessibility case: every label here has to grow rather than clip. */
@Preview(name = "Premium surfaces - large font", showBackground = true, fontScale = 1.5f, heightDp = 2000)
@Composable
private fun PremiumCatalogLargeFontPreview() {
    AiPlacementOsTheme { PremiumBody() }
}

/** The narrowest handset the app supports. Three stat tiles across is the tightest row in it. */
@Preview(name = "Premium surfaces - small phone", showBackground = true, widthDp = 320, heightDp = 1600)
@Composable
private fun PremiumCatalogSmallPhonePreview() {
    AiPlacementOsTheme { PremiumBody() }
}
