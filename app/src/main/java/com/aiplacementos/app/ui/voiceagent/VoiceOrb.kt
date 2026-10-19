package com.aiplacementos.app.ui.voiceagent

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.voice.realtime.ConversationState
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * The voice agent's state, drawn.
 *
 * ## Why a single component rather than per-state widgets
 *
 * The screen previously showed a 16dp [androidx.compose.material3.CircularProgressIndicator] and a
 * label for every busy state, so "listening to you" and "thinking" looked identical. In a voice
 * interface that distinction is the whole interface: it is the only thing telling a student
 * whether the microphone is open. One component owning every state means the mapping from state
 * to motion is in one readable place and cannot drift per screen.
 *
 * ## The honesty rule this component exists to keep
 *
 * **Expanding rings are drawn only for [ConversationState.Listening].** Nothing else in this file
 * may animate outward, because outward motion is what a person reads as "it is hearing me." Any
 * other state showing that would claim the microphone is open when it is not, which is the UI
 * equivalent of describing an unverified action as successful. [Speaking] gets inward-facing bars,
 * processing states get a rotating arc that never leaves the core, and [Idle] does not move.
 *
 * ## Accessibility
 *
 * Motion is suppressed entirely when the system animator scale is 0 -- the Android equivalent of
 * `prefers-reduced-motion`, which a student using "remove animations" has already asked for. The
 * component then renders its resting frame, so the state is still legible without movement, and
 * the state name is exposed to screen readers as a content description rather than being carried
 * by colour and motion alone.
 */
@Composable
fun VoiceOrb(
    state: ConversationState,
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 132.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val animated = rememberMotionEnabled()

    // One phase drives every state's motion, so transitions cannot desynchronise.
    val phase = if (animated) {
        rememberInfiniteTransition(label = "voice-orb").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "phase",
        ).value
    } else {
        // The resting frame. Not 0f: a mid-phase still frame shows the shape at a readable size
        // rather than collapsed to nothing.
        0.35f
    }

    // The core breathes a little while live and settles when idle. Animated as a target rather
    // than a loop so a state change eases instead of snapping.
    val coreScale by animateFloatAsState(
        targetValue = when (state) {
            is ConversationState.Idle -> 0.74f
            is ConversationState.Speaking -> 1.0f
            is ConversationState.Listening -> 0.92f
            else -> 0.84f
        },
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "core",
    )

    val accent: Color = when (state) {
        is ConversationState.Idle -> scheme.outline
        is ConversationState.Interrupted -> scheme.tertiary
        is ConversationState.Listening -> scheme.primary
        is ConversationState.Speaking -> scheme.secondary
        is ConversationState.Failed -> scheme.error
        is ConversationState.Closed -> scheme.outline
        else -> scheme.primary
    }

    Box(modifier = modifier.size(diameter).semantics { contentDescription = state.spokenLabel() }) {
        Canvas(modifier = Modifier.size(diameter)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val unit = min(size.width, size.height) / 2f

            when (state) {
                // Only here. See the honesty rule in the class comment.
                is ConversationState.Listening ->
                    drawListeningRings(centre, unit, accent, phase, animated)

                is ConversationState.Speaking ->
                    drawSpeakingBars(centre, unit, accent, phase, animated)

                is ConversationState.Idle -> Unit

                // Interrupted, Failed and Closed are all "nothing is happening right now":
                // a still ring, never the processing sweep, which would suggest the
                // session is still working on something.
                is ConversationState.Interrupted,
                is ConversationState.Failed,
                is ConversationState.Closed ->
                    drawRestingRing(centre, unit, accent)

                // Connecting, Transcribing, Thinking, ToolCalling, Generating: work is happening
                // on the server and the microphone is closed, so the motion stays inside the core.
                else -> drawProcessingArc(centre, unit, accent, phase, animated)
            }

            drawCircle(
                color = accent.copy(alpha = if (state is ConversationState.Idle) 0.30f else 0.92f),
                radius = unit * 0.34f * coreScale,
                center = centre,
            )
        }
    }
}

/** Three rings leaving the core, each a third of a cycle behind the last. */
private fun DrawScope.drawListeningRings(
    centre: Offset,
    unit: Float,
    accent: Color,
    phase: Float,
    animated: Boolean,
) {
    val ringCount = if (animated) 3 else 1
    for (index in 0 until ringCount) {
        val progress = (phase + index / 3f) % 1f
        // Fades as it expands, so the outer edge never reads as a hard boundary.
        val alpha = (1f - progress) * 0.45f
        drawCircle(
            color = accent.copy(alpha = alpha),
            radius = unit * (0.36f + progress * 0.62f),
            center = centre,
            style = Stroke(width = unit * 0.045f),
        )
    }
}

/** Five bars either side of the core, phase-offset so the shape reads as speech, not a meter. */
private fun DrawScope.drawSpeakingBars(
    centre: Offset,
    unit: Float,
    accent: Color,
    phase: Float,
    animated: Boolean,
) {
    val bars = 5
    val barWidth = unit * 0.085f
    val gap = unit * 0.075f
    val span = (bars - 1) * (barWidth + gap)
    for (index in 0 until bars) {
        // Fixed offsets rather than random: the same utterance always draws the same shape, and
        // nothing here is derived from the audio, which this component never sees.
        val offset = index * 0.37f
        val swing = if (animated) abs(sin((phase + offset) * Math.PI.toFloat() * 2f)) else 0.55f
        val height = unit * (0.26f + swing * 0.66f)
        val x = centre.x - span / 2f + index * (barWidth + gap)
        drawRoundRect(
            color = accent.copy(alpha = 0.55f + swing * 0.40f),
            topLeft = Offset(x - barWidth / 2f, centre.y - height / 2f),
            size = Size(barWidth, height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
        )
    }
}

/** A sweep that stays close to the core: work is happening, the microphone is not open. */
private fun DrawScope.drawProcessingArc(
    centre: Offset,
    unit: Float,
    accent: Color,
    phase: Float,
    animated: Boolean,
) {
    val radius = unit * 0.60f
    drawCircle(
        color = accent.copy(alpha = 0.16f),
        radius = radius,
        center = centre,
        style = Stroke(width = unit * 0.05f),
    )
    val sweep = if (animated) 90f else 70f
    drawArc(
        color = accent.copy(alpha = 0.85f),
        startAngle = if (animated) phase * 360f else -90f,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(centre.x - radius, centre.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = unit * 0.05f),
    )
}

/** Interrupted and idle-with-intent: present, but going nowhere. */
private fun DrawScope.drawRestingRing(centre: Offset, unit: Float, accent: Color) {
    drawCircle(
        color = accent.copy(alpha = 0.28f),
        radius = unit * 0.60f,
        center = centre,
        style = Stroke(width = unit * 0.05f),
    )
}

/**
 * Whether this device wants motion at all.
 *
 * `ANIMATOR_DURATION_SCALE` is 0 when a student has turned animations off, including via the
 * accessibility "remove animations" setting. Read once per composition; it is a system setting,
 * not something that changes mid-turn. Previews and tests get the resting frame, which keeps
 * screenshot output stable.
 */
@Composable
private fun rememberMotionEnabled(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) != 0f
        }.getOrDefault(true)
    }
}

/**
 * What to tell a screen reader.
 *
 * Deliberately phrased from the student's point of view, and deliberately explicit about the
 * microphone: "Listening to you" is the only value that may claim the microphone is open.
 */
internal fun ConversationState.spokenLabel(): String = when (this) {
    is ConversationState.Idle -> "Voice coach ready"
    is ConversationState.Connecting -> "Connecting"
    is ConversationState.Listening -> "Listening to you"
    is ConversationState.Transcribing -> "Working out what you said"
    is ConversationState.Thinking -> "Thinking"
    is ConversationState.ToolCalling -> "Looking something up"
    is ConversationState.Generating -> "Preparing a reply"
    is ConversationState.Speaking -> "Speaking"
    is ConversationState.Interrupted -> "Paused"
    is ConversationState.Reconnecting -> "Reconnecting"
    is ConversationState.Failed -> "Something went wrong"
    is ConversationState.Closed -> "Session ended"
}
