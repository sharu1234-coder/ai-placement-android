package com.aiplacementos.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.ui.theme.Elevation
import com.aiplacementos.app.ui.theme.Spacing

/**
 * Base rounded-card style shared by feature-specific cards (SkillCard, ReadinessCard, etc.).
 * `containerColor` defaults to the standard surface; pass a *Container role (e.g.
 * `MaterialTheme.colorScheme.primaryContainer`) for a tinted "hero" card like the Prepare Me
 * CTA or a readiness summary.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    /**
     * Defaults to the standard raised card, so every existing caller is unchanged. Pass
     * [Elevation.level0] for a supporting section that should recede: on a screen where
     * everything is raised equally, nothing reads as more important than anything else.
     */
    elevation: Dp = Elevation.level1,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = hairline(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            content()
        }
    }
}

/**
 * The edge that makes a card read as an object.
 *
 * ## Why a border was added to every card in the app
 *
 * The brand palette puts surfaces on `#F7F9FC` and cards on `#F7F9FC` -- the same value. The
 * only thing separating a card from the page was [Elevation.level1], a 1dp shadow, which on a
 * near-white background is very nearly invisible. Every card therefore dissolved into the page
 * and screens read as undifferentiated runs of text, which is most of what "it doesn't look
 * designed" actually means in practice.
 *
 * A hairline fixes that without adding weight. It is deliberately not a visible stroke: at 8%
 * of `onSurface` it is below the 3:1 UI contrast floor, which is correct here *because it
 * carries no information* -- it is a seam, not a control boundary or a status indicator. Every
 * card's actual content keeps its own AA-compliant contrast, and nothing is communicated by
 * this line alone, so making it stronger would add noise rather than accessibility.
 *
 * Derived from the colour scheme rather than written as a hex literal, so it follows the
 * palette and does not become the one shade nobody can find later.
 */
@Composable
private fun hairline(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
