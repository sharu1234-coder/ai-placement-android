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

// Semantic tokens outside the MD3 role set (no "success"/"warning" role exists in
// ColorScheme) -- used by SkillBar/ScoreRing to distinguish "matched skill" from
// "skill gap" without reaching for the alarm-coded `error` role for something that
// isn't an error. See ui/theme/SemanticColors.kt for the accessors.
val SuccessLight = Color(0xFF059669)
val WarningLight = Color(0xFFB45309)
