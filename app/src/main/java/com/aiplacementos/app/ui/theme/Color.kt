package com.aiplacementos.app.ui.theme

import androidx.compose.ui.graphics.Color

// Indigo/violet identity -- reads as a modern AI product, not a generic education-portal
// blue. Chosen deliberately over the stock Material Baseline palette to give AI Placement
// OS its own visual identity per UI_UX_REFERENCES.md section 8 ("premium but simple...
// friendly AI personality").

// Light palette
val PrimaryLight = Color(0xFF4F46E5) // Indigo 600
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE0E7FF) // Indigo 100
val OnPrimaryContainerLight = Color(0xFF1E1B4B) // Indigo 950

val SecondaryLight = Color(0xFF7C3AED) // Violet 600
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFEDE9FE) // Violet 100
val OnSecondaryContainerLight = Color(0xFF2E1065) // Violet 950

val TertiaryLight = Color(0xFF059669) // Emerald 600 -- readiness/progress accent
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFD1FAE5) // Emerald 100
val OnTertiaryContainerLight = Color(0xFF022C22)

val BackgroundLight = Color(0xFFFAFAFC)
val OnBackgroundLight = Color(0xFF1B1B23)
val SurfaceLight = Color(0xFFFAFAFC)
val OnSurfaceLight = Color(0xFF1B1B23)
val SurfaceVariantLight = Color(0xFFE4E2EF)
val OnSurfaceVariantLight = Color(0xFF46464F)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

// Dark palette
val PrimaryDark = Color(0xFFC4B8FF)
val OnPrimaryDark = Color(0xFF2E1C7A)
val PrimaryContainerDark = Color(0xFF4338CA)
val OnPrimaryContainerDark = Color(0xFFE0E7FF)

val SecondaryDark = Color(0xFFD5B8FF)
val OnSecondaryDark = Color(0xFF3E1A80)
val SecondaryContainerDark = Color(0xFF5B21B6)
val OnSecondaryContainerDark = Color(0xFFEDE9FE)

val TertiaryDark = Color(0xFF6EE7B7)
val OnTertiaryDark = Color(0xFF00391F)
val TertiaryContainerDark = Color(0xFF00693B)
val OnTertiaryContainerDark = Color(0xFFD1FAE5)

val BackgroundDark = Color(0xFF131318)
val OnBackgroundDark = Color(0xFFE5E2EC)
val SurfaceDark = Color(0xFF131318)
val OnSurfaceDark = Color(0xFFE5E2EC)
val SurfaceVariantDark = Color(0xFF46464F)
val OnSurfaceVariantDark = Color(0xFFC7C5D0)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

// Semantic tokens outside the MD3 role set (no "success"/"warning" role exists in
// ColorScheme) -- used by SkillBar/ScoreRing to distinguish "matched skill" from
// "skill gap" without reaching for the alarm-coded `error` role for something that
// isn't an error. See ui/theme/SemanticColors.kt for the light/dark accessors.
val SuccessLight = Color(0xFF059669)
val SuccessDark = Color(0xFF6EE7B7)
val WarningLight = Color(0xFFB45309)
val WarningDark = Color(0xFFFCD34D)
