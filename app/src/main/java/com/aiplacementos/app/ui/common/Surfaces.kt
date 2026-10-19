package com.aiplacementos.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiplacementos.app.ui.theme.Spacing

/**
 * The surfaces that give a screen a hierarchy, rather than one card repeated down the page.
 *
 * ## Why these exist
 *
 * Every screen in the app was built from exactly one container -- [AppCard] on the default
 * surface -- so a hero, a statistic, a category and a footnote all rendered as the same pale
 * rectangle. That is the single biggest reason the product reads as undesigned: nothing on a
 * screen is allowed to look more important than anything else, so the student's eye has
 * nowhere to land.
 *
 * Three surfaces, by role, and the names say when to reach for which:
 *
 * - [HeroCard] -- the one thing a screen exists to say. At most one per screen. Carries the
 *   brand gradient, so spending it twice makes neither instance special.
 * - [AccentCard] -- an emphasised item inside a list of ordinary ones ("your next step").
 *   A tinted ground plus a colour rail, which is far lighter than a second gradient.
 * - [AppCard] -- everything else, unchanged.
 *
 * All three take their colours from the scheme, never from hex literals, so the measured
 * contrast protections in `Color.kt` continue to apply.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
    ),
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentPadding: Dp = Spacing.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    // onPrimary is white on both brand colours, so every descendant that does not set its own
    // colour -- Text, Icon, the divider in a hero's footer -- lands on the right side of the
    // contrast floor without each call site remembering to pass it.
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier = modifier
                .clip(shape)
                .background(Brush.linearGradient(colors))
                .padding(contentPadding),
            content = content,
        )
    }
}

/**
 * An item that matters more than its neighbours, without becoming a second hero.
 *
 * The rail is 4dp of [accent] down the leading edge. It is deliberately a *shape* cue and not
 * only a colour one: the tinted ground alone would be the sole difference between this card
 * and an ordinary one for a student who cannot distinguish the tint, and status communicated
 * by colour alone is the accessibility failure this app has avoided elsewhere.
 */
@Composable
fun AccentCard(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.secondary,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentPadding: Dp = Spacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Row(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            // The rail's fillMaxHeight() needs the Row to know its tallest child first.
            // Without IntrinsicSize.Min the rail measures zero-height and silently vanishes.
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

/**
 * The label above a group of content.
 *
 * [eyebrow] is the small uppercase line some sections carry ("YOUR NEXT STEP"). It is optional
 * because most sections do not need one, and a page where every group has one is back to
 * having no hierarchy.
 *
 * [action] is a trailing affordance such as "View all". It is a plain clickable label rather
 * than a button: a second filled button in a section header competes with the screen's real
 * primary action.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    supporting: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Uppercase runs read as a dense block without it; this is the one place
                    // the app letterspaces, which is what keeps it reading as a label.
                    letterSpacing = 1.sp,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        action?.invoke()
    }
}

/**
 * An icon on its own tinted ground.
 *
 * The single most reusable premium cue in the app: it turns a bare glyph into a deliberate
 * object, and varying only the tint across a grid gives each category its own identity while
 * keeping one shape language. [tint] is used for the glyph and, at 12% alpha, for the ground
 * -- derived from one colour rather than two, so a caller cannot pair a tint with a ground it
 * cannot be read on.
 */
@Composable
fun IconChip(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    contentDescription: String? = null,
) {
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.medium,
        color = tint.copy(alpha = 0.12f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}
