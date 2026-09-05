package com.aiplacementos.app.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.ui.theme.Spacing

/**
 * Reusable skeleton-loading primitives -- static gray placeholders shaped like the content
 * they stand in for, pulsing gently while real data loads. Respects the system's "Remove
 * animations" accessibility setting (see rememberReduceMotion() in ReduceMotion.kt, extracted
 * from what used to be a private copy of this exact check living only here): the pulse is
 * skipped and a fixed mid-alpha placeholder is shown instead, per the reduced-motion
 * requirement -- never an infinite animation the user asked to turn off.
 */
@Composable
private fun rememberSkeletonAlpha(): Float {
    if (rememberReduceMotion()) return 0.6f
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    return alpha
}

@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val alpha = rememberSkeletonAlpha()
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant)
            .alpha(alpha),
    )
}

@Composable
fun SkeletonCircle(size: Dp, modifier: Modifier = Modifier) {
    SkeletonBox(modifier = modifier.height(size).width(size), shape = CircleShape)
}

/** [widthFractions] lets the last line run shorter, like real wrapped text -- defaults to
 * a shorter final line whenever there's more than one. */
@Composable
fun SkeletonText(
    modifier: Modifier = Modifier,
    lines: Int = 1,
    lineHeight: Dp = 14.dp,
    widthFractions: List<Float> = List(lines) { if (it == lines - 1 && lines > 1) 0.6f else 1f },
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        repeat(lines) { index ->
            SkeletonBox(modifier = Modifier.fillMaxWidth(widthFractions.getOrElse(index) { 1f }).height(lineHeight))
        }
    }
}

@Composable
fun SkeletonRow(modifier: Modifier = Modifier, circleSize: Dp = 40.dp, textLines: Int = 2) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SkeletonCircle(size = circleSize)
        Spacer(modifier = Modifier.width(Spacing.sm))
        SkeletonText(modifier = Modifier.weight(1f), lines = textLines)
    }
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier, circleSize: Dp = 40.dp, textLines: Int = 2) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        SkeletonRow(circleSize = circleSize, textLines = textLines)
    }
}

@Composable
fun SkeletonList(
    count: Int,
    modifier: Modifier = Modifier,
    itemContent: @Composable () -> Unit = { SkeletonCard() },
) {
    // mergeDescendants: a screen reader must announce "Loading" once for the whole
    // placeholder list, not silently skip it (skeleton boxes carry no text of their own to
    // announce) and not announce it once per repeated item.
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(count) { itemContent() }
    }
}
