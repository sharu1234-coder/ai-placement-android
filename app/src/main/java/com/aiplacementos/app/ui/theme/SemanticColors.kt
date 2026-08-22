package com.aiplacementos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * "Matched skill" / "skill gap" aren't errors, so they don't belong on MD3's `error` role --
 * these are the app's own semantic tokens (see Color.kt), theme-aware like everything else.
 */
@Composable
fun successColor(): Color = if (isSystemInDarkTheme()) SuccessDark else SuccessLight

@Composable
fun warningColor(): Color = if (isSystemInDarkTheme()) WarningDark else WarningLight
