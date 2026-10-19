package com.aiplacementos.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * The app's illustrations, drawn rather than shipped.
 *
 * ## Why Canvas and not artwork
 *
 * Empty states are where this product looked least finished -- a sentence and a button in the
 * middle of an otherwise blank screen. The fix is an illustration, but a raster set would add
 * weight to the APK for every density, and a vector set would still be a fixed palette pasted
 * next to a themed one.
 *
 * These take their colours from the caller, which takes them from the scheme. They cost a few
 * draw calls, scale to any size, and cannot drift out of step with the brand palette.
 *
 * ## They are decoration, and say so
 *
 * Every one clears its semantics. The sentence beside the drawing is what carries the meaning,
 * and a screen reader announcing "image" between the heading and the explanation adds nothing
 * for the student and one more thing to tab past.
 *
 * All geometry is expressed as a fraction of the drawing's own size, with the extremes kept
 * inside 0..1 on both axes, so nothing is clipped at any size the caller picks.
 */

/**
 * A winding route with milestones and a flag at the end. For the roadmap's empty state: it
 * says "a path with stages", which is exactly what generating a roadmap produces.
 */
@Composable
fun RouteIllustration(
    modifier: Modifier = Modifier,
    track: Color,
    line: Color,
    accent: Color,
    ground: Color,
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val stroke = h * 0.075f

        val route = Path().apply {
            moveTo(w * 0.10f, h * 0.88f)
            cubicTo(w * 0.36f, h * 0.94f, w * 0.20f, h * 0.62f, w * 0.46f, h * 0.62f)
            cubicTo(w * 0.72f, h * 0.62f, w * 0.60f, h * 0.44f, w * 0.84f, h * 0.42f)
        }
        drawPath(route, track, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawPath(
            route,
            line,
            style = Stroke(
                width = stroke * 0.22f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 0.55f, stroke * 0.75f)),
            ),
        )

        listOf(Offset(w * 0.10f, h * 0.88f), Offset(w * 0.46f, h * 0.62f)).forEach { node ->
            drawCircle(ground, radius = stroke * 0.60f, center = node)
            drawCircle(line, radius = stroke * 0.60f, center = node, style = Stroke(width = stroke * 0.20f))
        }

        val tip = Offset(w * 0.84f, h * 0.42f)
        val poleTop = Offset(tip.x, h * 0.14f)
        drawLine(accent, start = tip, end = poleTop, strokeWidth = stroke * 0.22f, cap = StrokeCap.Round)
        drawPath(
            Path().apply {
                moveTo(poleTop.x, poleTop.y)
                lineTo(poleTop.x + w * 0.12f, poleTop.y + h * 0.055f)
                lineTo(poleTop.x, poleTop.y + h * 0.11f)
                close()
            },
            accent,
        )
        drawCircle(accent, radius = stroke * 0.42f, center = tip)
    }
}

/**
 * Concentric rings with an arrow arriving at the centre. For the career-goal empty state:
 * a target is the one picture of "pick what you are aiming at" that needs no caption.
 */
@Composable
fun TargetIllustration(
    modifier: Modifier = Modifier,
    ring: Color,
    accent: Color,
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val centre = Offset(w * 0.56f, h * 0.44f)
        val r = minOf(w, h) * 0.34f
        val band = r * 0.16f

        drawCircle(ring.copy(alpha = 0.35f), radius = r, center = centre, style = Stroke(width = band))
        drawCircle(ring.copy(alpha = 0.60f), radius = r * 0.62f, center = centre, style = Stroke(width = band))
        drawCircle(accent, radius = r * 0.22f, center = centre)

        // The arrow stops short of the bullseye rather than overlapping it, so the centre
        // still reads as a single solid mark instead of a smudge.
        drawLine(
            accent,
            start = Offset(w * 0.08f, h * 0.90f),
            end = Offset(centre.x - r * 0.26f, centre.y + r * 0.26f),
            strokeWidth = band * 0.80f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Four ascending bars with the most recent one picked out. For progress: the shape of
 * improvement, without implying any particular figure.
 */
@Composable
fun GrowthIllustration(
    modifier: Modifier = Modifier,
    track: Color,
    accent: Color,
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val barWidth = w * 0.14f
        val gap = w * 0.06f
        val baseline = h * 0.92f
        val fractions = listOf(0.34f, 0.50f, 0.68f, 0.92f)

        fractions.forEachIndexed { index, fraction ->
            val x = w * 0.08f + index * (barWidth + gap)
            val top = baseline - (baseline - h * 0.10f) * fraction
            drawRoundRect(
                color = if (index == fractions.lastIndex) accent else track,
                topLeft = Offset(x, top),
                size = Size(barWidth, baseline - top),
                cornerRadius = CornerRadius(barWidth * 0.35f, barWidth * 0.35f),
            )
        }
    }
}

/**
 * A summit with a dashed route and a flag. For the learning roadmap's empty state, where the
 * promise is a climb with a destination rather than an unordered list of topics.
 */
@Composable
fun SummitIllustration(
    modifier: Modifier = Modifier,
    track: Color,
    line: Color,
    accent: Color,
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val base = h * 0.86f

        drawCircle(accent.copy(alpha = 0.25f), radius = h * 0.11f, center = Offset(w * 0.18f, h * 0.20f))

        drawPath(
            Path().apply {
                moveTo(w * 0.06f, base)
                lineTo(w * 0.44f, h * 0.26f)
                lineTo(w * 0.82f, base)
                close()
            },
            track,
        )
        // The cap is the same shape clipped by a horizontal cut, drawn as its own small
        // triangle rather than with a clip layer -- cheaper, and exact at any size.
        drawPath(
            Path().apply {
                moveTo(w * 0.44f, h * 0.26f)
                lineTo(w * 0.58f, h * 0.44f)
                lineTo(w * 0.30f, h * 0.44f)
                close()
            },
            line.copy(alpha = 0.55f),
        )
        drawPath(
            Path().apply {
                moveTo(w * 0.16f, base)
                cubicTo(w * 0.36f, h * 0.76f, w * 0.26f, h * 0.52f, w * 0.44f, h * 0.30f)
            },
            line,
            style = Stroke(
                width = h * 0.025f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(h * 0.05f, h * 0.06f)),
            ),
        )

        val peak = Offset(w * 0.44f, h * 0.26f)
        val poleTop = Offset(peak.x, h * 0.06f)
        drawLine(accent, peak, poleTop, strokeWidth = h * 0.022f, cap = StrokeCap.Round)
        drawPath(
            Path().apply {
                moveTo(poleTop.x, poleTop.y)
                lineTo(poleTop.x + w * 0.16f, poleTop.y + h * 0.05f)
                lineTo(poleTop.x, poleTop.y + h * 0.10f)
                close()
            },
            accent,
        )
    }
}

/**
 * A sheet with completed rows. For assessment surfaces: it says "a set of questions with
 * answers recorded" without implying any particular score.
 */
@Composable
fun AssessmentIllustration(
    modifier: Modifier = Modifier,
    sheet: Color,
    line: Color,
    accent: Color,
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val radius = CornerRadius(w * 0.08f, w * 0.08f)

        drawRoundRect(sheet, topLeft = Offset(w * 0.14f, h * 0.10f), size = Size(w * 0.68f, h * 0.80f), cornerRadius = radius)
        drawRoundRect(
            accent,
            topLeft = Offset(w * 0.32f, h * 0.04f),
            size = Size(w * 0.32f, h * 0.12f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
        )

        listOf(0.32f, 0.50f, 0.68f).forEachIndexed { index, y ->
            val cx = w * 0.26f
            val cy = h * y
            val r = h * 0.045f
            if (index == 2) {
                drawCircle(line.copy(alpha = 0.35f), radius = r, center = Offset(cx, cy), style = Stroke(width = h * 0.018f))
            } else {
                drawCircle(line, radius = r, center = Offset(cx, cy))
            }
            drawLine(
                line.copy(alpha = if (index == 2) 0.25f else 0.45f),
                start = Offset(w * 0.36f, cy),
                end = Offset(w * 0.72f, cy),
                strokeWidth = h * 0.035f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Two bubbles and a waveform. For the voice surfaces, where the subject is a spoken exchange
 * rather than a document.
 */
@Composable
fun ConversationIllustration(
    modifier: Modifier = Modifier,
    bubble: Color,
    reply: Color,
    accent: Color,
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height

        drawRoundRect(
            bubble,
            topLeft = Offset(w * 0.06f, h * 0.12f),
            size = Size(w * 0.58f, h * 0.36f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
        )
        drawRoundRect(
            reply,
            topLeft = Offset(w * 0.34f, h * 0.52f),
            size = Size(w * 0.58f, h * 0.34f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
        )

        // A waveform inside the reply, because the reply is the spoken half.
        listOf(0.22f, 0.52f, 0.34f, 0.64f, 0.28f).forEachIndexed { index, amplitude ->
            val x = w * (0.44f + index * 0.09f)
            val half = h * amplitude / 2f
            drawLine(
                accent,
                start = Offset(x, h * 0.69f - half),
                end = Offset(x, h * 0.69f + half),
                strokeWidth = w * 0.035f,
                cap = StrokeCap.Round,
            )
        }
    }
}
