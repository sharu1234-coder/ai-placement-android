package com.aiplacementos.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Animated circular score indicator -- the doc's "ScoreRing" design-system component
 * (UI_UX_REFERENCES.md section 9), used for readiness/match/result scores. Animates from 0
 * once on first composition/value change, never re-animates on recomposition of the same
 * value (LaunchedEffect is keyed on percent).
 */
@Composable
fun ScoreRing(
    percent: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    size: androidx.compose.ui.unit.Dp = 120.dp,
    ringColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    // Overrides the default "N%" center text -- e.g. "82/100" for the ATS Check score
    // ring. Last param, appended rather than inserted, so no existing positional call
    // site is at risk.
    displayText: String? = null,
) {
    val animatedFraction = remember { Animatable(0f) }
    LaunchedEffect(percent) {
        animatedFraction.animateTo(
            targetValue = (percent.coerceIn(0, 100)) / 100f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        )
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = size.toPx() * 0.09f
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedFraction.value,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = displayText ?: "${(animatedFraction.value * 100).roundToInt()}%",
                style = MaterialTheme.typography.headlineSmall,
            )
            if (label != null) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
