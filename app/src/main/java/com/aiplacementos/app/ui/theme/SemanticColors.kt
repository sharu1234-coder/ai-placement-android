package com.aiplacementos.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * "Matched skill" / "skill gap" aren't errors, so they don't belong on MD3's `error` role --
 * these are the app's own semantic tokens (see Color.kt). Always light -- the app has no
 * dark theme, so unlike an earlier version of this file, these must not branch on the
 * device's system dark-mode setting independently of AiPlacementOsTheme.
 */
@Composable
fun successColor(): Color = SuccessLight

@Composable
fun warningColor(): Color = WarningLight
