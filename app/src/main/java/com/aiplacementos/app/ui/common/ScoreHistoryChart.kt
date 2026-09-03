package com.aiplacementos.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Minimal hand-drawn line chart -- points + connecting line only, no axis chrome or
 * scrubbing in v1. Matches ScoreRing's existing precedent for a Canvas-based
 * visualization rather than pulling in a new charting dependency (none exists in this
 * project's build.gradle.kts). Expects scores oldest-first.
 */
@Composable
fun ScoreHistoryChart(
    scores: List<Int>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (scores.size < 2) return

    // A hand-drawn Canvas has zero built-in semantics -- a screen-reader user would get
    // nothing from this chart otherwise. A short trend summary is more useful here than
    // per-point detail, which the raw chart doesn't cleanly expose either.
    val trend = scores.last() - scores.first()
    val trendDescription = when {
        trend > 0 -> "up ${trend} points"
        trend < 0 -> "down ${-trend} points"
        else -> "unchanged"
    }
    val description = "Score trend chart: from ${scores.first()} to ${scores.last()} " +
        "over ${scores.size} attempts, $trendDescription."

    Canvas(
        modifier = modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = description },
    ) {
        val minScore = (scores.min() - 5).coerceAtLeast(0)
        val maxScore = (scores.max() + 5).coerceAtMost(100)
        val range = (maxScore - minScore).coerceAtLeast(1)
        val stepX = if (scores.size > 1) size.width / (scores.size - 1) else 0f

        val points = scores.mapIndexed { index, score ->
            val fraction = (score - minScore).toFloat() / range
            Offset(x = stepX * index, y = size.height - (fraction * size.height))
        }

        for (i in 0 until points.size - 1) {
            drawLine(color = lineColor, start = points[i], end = points[i + 1], strokeWidth = 6f)
        }
        points.forEach { point -> drawCircle(color = lineColor, radius = 8f, center = point) }
    }
}
