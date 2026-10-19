package com.aiplacementos.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * TALVIORA AI brand palette -- deep navy foundation, electric blue and cyan for interactive and
 * intelligent surfaces, restrained gold for selective emphasis.
 *
 * ## Why the token names did not change
 *
 * Every name below is the one the previous indigo/violet palette used. 97 screen files and 116
 * users of the design system read these through `MaterialTheme.colorScheme`, so re-pointing the
 * tokens re-brands the entire app in one reviewable change, with no screen edited. Renaming any
 * token here would have turned a palette decision into a 97-file refactor.
 *
 * ## Why gold and cyan are darker than a brand deck would draw them
 *
 * Measured contrast against the light surface (#F7F9FC), WCAG 2.1:
 *
 * | pair | ratio | verdict |
 * |---|---|---|
 * | bright cyan `#06B6D4` with white text | **2.43** | fails AA *and* the 3:1 UI floor |
 * | bright gold `#C99A2E` with white text | **2.58** | fails AA *and* the 3:1 UI floor |
 * | deepened cyan `#0B6E75` with white text | 6.00 | passes AA |
 * | restrained gold `#8A6100` with white text | 5.54 | passes AA |
 *
 * So the showroom versions of cyan and gold cannot carry text or act as a thin stroke on this
 * background -- not as a matter of taste but of measurement. They appear only as *containers*
 * holding navy text, where they measure 14:1. "Restrained gold accents" is therefore literal:
 * gold is a tertiary emphasis role, never a page surface.
 *
 * Every pair below is verified by `BrandPaletteContrastTest`, which computes the ratios from these
 * very values, so a later "slightly brighter" edit fails a test instead of shipping.
 */

// ---------------------------------------------------------------- brand foundation

/** Deep navy. The brand's anchor: app bars, primary actions, headings. */
val PrimaryLight = Color(0xFF0A2A5E)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFDCE6F7)
val OnPrimaryContainerLight = Color(0xFF071E45)

/** Electric blue. Interactive and "intelligent" surfaces -- the AI-facing half of the identity. */
val SecondaryLight = Color(0xFF0B5FD4)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFDBE9FE)
val OnSecondaryContainerLight = Color(0xFF071E45)

/**
 * Restrained gold. Selective emphasis only -- an achievement, a highlighted recommendation.
 *
 * Deliberately not the bright metallic gold: that measures 2.58:1 with white text here. Used
 * sparingly by design; a screen where everything is gold has no emphasis at all.
 */
val TertiaryLight = Color(0xFF8A6100)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFBF0D9)
val OnTertiaryContainerLight = Color(0xFF071E45)

// ---------------------------------------------------------------- surfaces

/**
 * Clean, faintly cool neutrals rather than pure white: a surface biased very slightly toward the
 * navy keeps the palette reading as one family, where #FFFFFF reads as unstyled.
 *
 * The page background is **not** navy. "Deep navy foundation" is the brand's anchor colour, not an
 * instruction to put readable content on a dark gradient.
 */
val BackgroundLight = Color(0xFFF7F9FC)
val OnBackgroundLight = Color(0xFF15223B)
val SurfaceLight = Color(0xFFF7F9FC)
val OnSurfaceLight = Color(0xFF15223B)
val SurfaceVariantLight = Color(0xFFE2E8F2)
val OnSurfaceVariantLight = Color(0xFF4A5568)

// ---------------------------------------------------------------- feedback

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

/**
 * Semantic tokens outside the Material role set, because MD3 has no `success`/`warning` role.
 * `SkillBar` and `ScoreRing` use these to distinguish "matched skill" from "skill gap" without
 * borrowing the alarm-coded `error` role for something that is not an error.
 *
 * Both kept from the previous palette: they are status colours, not brand colours, and recolouring
 * a success state to match a rebrand would make green mean something new on every progress screen.
 */
val SuccessLight = Color(0xFF059669)
val WarningLight = Color(0xFFB45309)

/**
 * Cyan, for the voice and AI surfaces specifically.
 *
 * The brief asks for "electric blue and cyan for interactive and intelligent features". Electric
 * blue is the `secondary` role above; this is the companion accent, offered as an app token rather
 * than squeezed into a Material role it would fight with.
 *
 * Two measurements shaped this value. The bright cyan cannot carry text (2.43:1). And a
 * first attempt at a text-safe cyan, #0E7490, sat only **22 degrees** from the electric
 * blue above while matching it in lightness -- two roles a viewer could not reliably tell
 * apart. #0B6E75 is 30.9 degrees away at 6.0:1 with white text.
 *
 * The honest consequence: a cyan dark enough for text reads as deep teal rather than the
 * bright cyan of a brand deck. That is the cost of the contrast requirement, not a
 * misreading of the brief.
 */
val AccentCyanLight = Color(0xFF0B6E75)
val OnAccentCyanLight = Color(0xFFFFFFFF)
val AccentCyanContainerLight = Color(0xFFCFF4FC)
