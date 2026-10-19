package com.aiplacementos.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
)

@Composable
fun AiPlacementOsTheme(
    // Always light. The product identity is the light, faintly cool surface system in
    // Color.kt -- a deep navy brand anchor on a near-white page, not a dark theme; dark mode
    // is deliberately not offered, regardless of the device's system setting.
    //
    // This comment used to describe "the light lavender/violet surface system from
    // UI_UX_REFERENCES.md". The palette was re-pointed to navy/electric blue/gold on
    // 2026-10-18 and the comment was left behind, so the only surviving record of the
    // single-theme decision was also describing colours the app no longer uses. The decision
    // is unchanged; its justification now names the palette that actually ships.
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
