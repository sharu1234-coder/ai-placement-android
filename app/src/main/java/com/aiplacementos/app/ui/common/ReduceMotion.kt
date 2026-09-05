package com.aiplacementos.app.ui.common

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the system's "Remove animations" accessibility setting is on
 * (Settings.Global.ANIMATOR_DURATION_SCALE == 0). Originally implemented inline in Skeleton.kt
 * for its own pulse animation; extracted here so every animated composable in the app can
 * share one check instead of each reimplementing (or forgetting to implement) it -- see
 * ScoreRing and SkillBar, whose value-reveal animation would otherwise run unconditionally
 * even with this setting on.
 *
 * When this is true, a caller should skip straight to the final value instead of animating --
 * never invert the meaning (e.g. animating *faster*), since the system setting means "no
 * motion," not "less motion."
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
