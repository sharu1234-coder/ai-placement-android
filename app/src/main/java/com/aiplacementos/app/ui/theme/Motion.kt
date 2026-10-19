package com.aiplacementos.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * The app's motion vocabulary, as tokens.
 *
 * ## Why durations are named by role, not by number
 *
 * Before this file, every animated composable chose its own duration inline: the recording
 * pulse used 700ms, the skeleton shimmer its own value, `ScoreRing` another. Nothing was
 * wrong with any single number, but together they read as unrelated effects rather than one
 * product moving in one way -- which is most of what separates a considered interface from a
 * collection of animations.
 *
 * Three durations cover everything here, and the names say when to reach for which:
 *
 * - [quick] -- a control acknowledging a touch. Fast enough that it feels like the press
 *   itself rather than a consequence of it.
 * - [standard] -- the default for anything appearing, disappearing or moving on screen.
 * - [emphasized] -- reserved for a moment that genuinely deserves attention: a score being
 *   revealed, a result arriving. Spending it on routine transitions is what makes an
 *   interface feel slow, so it is the exception.
 *
 * ## Accessibility is not optional here
 *
 * Every caller must gate its animation on `rememberReduceMotion()`. These tokens deliberately
 * do **not** read that setting themselves: a duration cannot decide whether to animate, only
 * how long to take, and a token that silently returned 0 would hide the fact that a composable
 * never checked. The check belongs at the call site, where the final value is known.
 */
object Motion {
    /** A control acknowledging a press. */
    const val quick = 120

    /** The default for content entering, leaving or moving. */
    const val standard = 240

    /** A moment worth noticing -- a revealed score, an arriving result. Used sparingly. */
    const val emphasized = 400

    /**
     * The distance content travels when it rises into place.
     *
     * Small on purpose. A long slide draws attention to the animation; a short one reads as
     * the content settling, which is the intent. In dp, applied by the caller.
     */
    const val riseDp = 12

    /**
     * Material 3's emphasized-decelerate curve: quick to start, slow to settle.
     *
     * The right default for something *arriving*, because the deceleration is what makes it
     * look like it came to rest rather than stopped.
     */
    val enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** The mirror of [enter], for something leaving: slow to start, quick to go. */
    val exit: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** Symmetric and unobtrusive -- for a value changing in place, not arriving or leaving. */
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
}
