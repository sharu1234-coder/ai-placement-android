package com.aiplacementos.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Everything the design system defines, on one screen, so it can be looked at.
 *
 * ## Why this file exists
 *
 * The 2026-10-18 UI audit found **zero `@Preview` functions in the entire `ui/` tree** -- 97
 * screen files with no way to render any of them without building and installing the app. That is
 * the single biggest obstacle to doing careful visual work here: a change to the type scale or a
 * shared component touches every screen, and nothing could show the result.
 *
 * This catalog is the surface that was missing. It uses the preview tooling the project already
 * depends on (`androidx.ui.tooling.preview`), so it adds no dependency and no runtime behaviour --
 * nothing in the app navigates here. It exists to be opened in the IDE's preview pane, and to be
 * the obvious first target if screenshot testing is ever authorised.
 *
 * ## What it deliberately shows
 *
 * The **large-font preview** at the bottom is the point of the whole file. `fontScale = 1.5f` is
 * what a student with enlarged system text actually sees, and it is where a fixed-height row or a
 * cramped two-column layout breaks. Reviewing that preview is cheaper than discovering it on a
 * handset.
 *
 * It renders the tokens, not a mock screen. A fake dashboard here would drift from the real one
 * and become a lie about the product; tokens cannot drift from themselves.
 */

@Composable
private fun CatalogSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        content()
    }
}

@Composable
private fun Swatch(name: String, fill: Color, textOn: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(fill, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            // "Aa" on the role's own `on` colour: a swatch that does not show its text colour
            // cannot show whether the pair is readable, which is the only thing worth checking.
            Text(text = "Aa", color = textOn, style = MaterialTheme.typography.labelMedium)
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = Spacing.md),
        )
    }
}

/** The type scale, every role, in order. */
@Composable
private fun TypographyCatalog() {
    val type = MaterialTheme.typography
    CatalogSection("Typography") {
        Column {
            Text("Headline large", style = type.headlineLarge)
            Text("Headline medium", style = type.headlineMedium)
            Text("Headline small", style = type.headlineSmall)
            Text("Title large", style = type.titleLarge)
            Text("Title medium", style = type.titleMedium)
            Text("Title small", style = type.titleSmall)
            Text("Body large -- the readable default for paragraphs.", style = type.bodyLarge)
            Text("Body medium -- supporting copy and list detail.", style = type.bodyMedium)
            Text("Body small -- metadata and timestamps.", style = type.bodySmall)
            Text("LABEL LARGE", style = type.labelLarge)
            Text("LABEL MEDIUM", style = type.labelMedium)
            Text("LABEL SMALL", style = type.labelSmall)
        }
    }
}

/** Colour roles paired with the text colour that sits on them. */
@Composable
private fun ColourCatalog() {
    val scheme = MaterialTheme.colorScheme
    CatalogSection("Colour roles") {
        Column {
            Swatch("primary", scheme.primary, scheme.onPrimary)
            Swatch("primaryContainer", scheme.primaryContainer, scheme.onPrimaryContainer)
            Swatch("secondary", scheme.secondary, scheme.onSecondary)
            Swatch("secondaryContainer", scheme.secondaryContainer, scheme.onSecondaryContainer)
            Swatch("tertiary", scheme.tertiary, scheme.onTertiary)
            Swatch("surface", scheme.surface, scheme.onSurface)
            Swatch("surfaceVariant", scheme.surfaceVariant, scheme.onSurfaceVariant)
            Swatch("error", scheme.error, scheme.onError)
            // The app's own roles, outside the Material set. See SemanticColors.kt on why
            // "skill gap" must not borrow the alarm-coded error role.
            // The voice/AI accent, and the gold emphasis role. Both are shown at the depth
            // they are actually defined at -- see Color.kt on why the brighter versions
            // cannot carry text on this background.
            Swatch("accentCyan (app token)", accentCyanColor(), onAccentCyanColor())
            Swatch("accentCyanContainer", accentCyanContainerColor(), scheme.onPrimaryContainer)
            Swatch("success (app token)", successColor(), Color.White)
            Swatch("warning (app token)", warningColor(), Color.White)
        }
    }
}

/** The spacing scale, drawn to scale. */
@Composable
private fun SpacingCatalog() {
    CatalogSection("Spacing scale") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            listOf(
                "xs" to Spacing.xs,
                "sm" to Spacing.sm,
                "md" to Spacing.md,
                "lg" to Spacing.lg,
                "xl" to Spacing.xl,
                "xxl" to Spacing.xxl,
                "chipGap" to Spacing.chipGap,
            ).forEach { (name, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(72.dp),
                    )
                    Box(
                        modifier = Modifier
                            .height(12.dp)
                            .width(value)
                            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
                    )
                }
            }
        }
    }
}

/** Corner radii, so button and card shapes can be compared side by side. */
@Composable
private fun ShapeCatalog() {
    val shapes = MaterialTheme.shapes
    CatalogSection("Shapes") {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(
                "xs" to shapes.extraSmall,
                "sm" to shapes.small,
                "md" to shapes.medium,
                "lg" to shapes.large,
                "xl" to shapes.extraLarge,
            ).forEach { (name, shape) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, shape),
                    )
                    Text(text = name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun CatalogBody() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
        ) {
            Text(
                text = "TALVIORA AI design system",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            Text(
                text = "Your Potential, Accelerated.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.lg),
            )
            TypographyCatalog()
            ColourCatalog()
            SpacingCatalog()
            ShapeCatalog()
        }
    }
}

@Preview(name = "Design system", showBackground = true, heightDp = 1800)
@Composable
private fun DesignSystemCatalogPreview() {
    AiPlacementOsTheme { CatalogBody() }
}

/**
 * The accessibility check that matters most, and the reason this file is worth keeping.
 *
 * `fontScale = 1.5f` is a real setting a student may be using. Every fixed-height row, every
 * side-by-side pair and every truncating label fails here first.
 */
@Preview(name = "Design system - large font", showBackground = true, fontScale = 1.5f, heightDp = 2400)
@Composable
private fun DesignSystemCatalogLargeFontPreview() {
    AiPlacementOsTheme { CatalogBody() }
}

/** A narrow phone, where horizontal rows run out of room. */
@Preview(name = "Design system - small phone", showBackground = true, widthDp = 320, heightDp = 1800)
@Composable
private fun DesignSystemCatalogSmallPhonePreview() {
    AiPlacementOsTheme { CatalogBody() }
}
