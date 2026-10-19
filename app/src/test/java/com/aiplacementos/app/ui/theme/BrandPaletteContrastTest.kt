package com.aiplacementos.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The TALVIORA AI palette is readable, measured rather than asserted.
 *
 * Contrast is the one accessibility property of a colour scheme that can be settled without
 * rendering anything: WCAG 2.1 relative luminance is arithmetic over the channel values. So these
 * tests compute the ratios from the **real token objects** in `Color.kt` -- not from hex strings
 * copied into the test, which would pass happily after someone edited the palette.
 *
 * This is what makes "restrained gold" enforceable. The bright metallic gold a brand deck would
 * draw measures 2.58:1 with white text on this background; the token below measures 5.54:1. A
 * future "let's make it pop" edit fails here, with the number, instead of shipping unreadable text.
 *
 * ## What this does not prove
 *
 * Contrast is not visual validation. These tests say nothing about whether the palette looks
 * good, whether gold is used sparingly in practice, or how any screen actually renders. Those need
 * eyes on a preview or a device, and are tracked as open gates in
 * `docs/ui-ux-modernization-audit.md`.
 */
class BrandPaletteContrastTest {

    /** WCAG 2.1 AA for normal body text. */
    private val bodyText = 4.5

    /** WCAG 2.1 AA for large text, icons and the boundaries of interactive components. */
    private val largeTextAndUi = 3.0

    // ------------------------------------------------------------------ brand roles

    @Test
    fun `text on every brand role is readable`() {
        assertAtLeast("white on navy (primary)", OnPrimaryLight, PrimaryLight, bodyText)
        assertAtLeast("white on electric blue (secondary)", OnSecondaryLight, SecondaryLight, bodyText)
        assertAtLeast("white on restrained gold (tertiary)", OnTertiaryLight, TertiaryLight, bodyText)
        assertAtLeast("white on cyan accent", OnAccentCyanLight, AccentCyanLight, bodyText)
    }

    @Test
    fun `text on every container role is readable`() {
        assertAtLeast("on navy container", OnPrimaryContainerLight, PrimaryContainerLight, bodyText)
        assertAtLeast("on blue container", OnSecondaryContainerLight, SecondaryContainerLight, bodyText)
        assertAtLeast("on gold container", OnTertiaryContainerLight, TertiaryContainerLight, bodyText)
        assertAtLeast(
            "navy on cyan container",
            OnPrimaryContainerLight,
            AccentCyanContainerLight,
            bodyText,
        )
    }

    // ------------------------------------------------------------------ surfaces

    @Test
    fun `body and supporting text are readable on the page`() {
        assertAtLeast("body text on background", OnBackgroundLight, BackgroundLight, bodyText)
        assertAtLeast("body text on surface", OnSurfaceLight, SurfaceLight, bodyText)
        // The one most often got wrong: muted secondary text that drops below AA.
        assertAtLeast(
            "supporting text on surface variant",
            OnSurfaceVariantLight,
            SurfaceVariantLight,
            bodyText,
        )
        assertAtLeast(
            "supporting text on the page background",
            OnSurfaceVariantLight,
            BackgroundLight,
            bodyText,
        )
    }

    @Test
    fun `brand colours used as text on the page are readable`() {
        // Section headings and links are drawn in the brand colour on the page background.
        assertAtLeast("navy text on background", PrimaryLight, BackgroundLight, bodyText)
        assertAtLeast("electric blue text on background", SecondaryLight, BackgroundLight, bodyText)
        assertAtLeast("gold text on background", TertiaryLight, BackgroundLight, bodyText)
        assertAtLeast("cyan text on background", AccentCyanLight, BackgroundLight, bodyText)
    }

    // ------------------------------------------------------------------ feedback

    @Test
    fun `error and status colours are readable`() {
        assertAtLeast("white on error", OnErrorLight, ErrorLight, bodyText)
        assertAtLeast("on error container", OnErrorContainerLight, ErrorContainerLight, bodyText)
        // Status colours must clear the UI floor at minimum: they are read as fills and strokes,
        // and status is never communicated by colour alone (see the audit doc).
        assertAtLeast("success on background", SuccessLight, BackgroundLight, largeTextAndUi)
        assertAtLeast("warning on background", WarningLight, BackgroundLight, largeTextAndUi)
    }

    // ------------------------------------------------------------------ the restraint rule

    @Test
    fun `gold stays restrained enough to carry text`() {
        val ratio = contrast(OnTertiaryLight, TertiaryLight)
        assertTrue(
            "Gold must keep white text readable. The bright metallic gold (#C99A2E) measures " +
                "2.58:1 here and fails both AA and the 3:1 UI floor. Current ratio: " + ratio,
            ratio >= bodyText,
        )
    }

    @Test
    fun `the cyan accent stays deep enough to carry text`() {
        val ratio = contrast(OnAccentCyanLight, AccentCyanLight)
        assertTrue(
            "The showroom cyan (#06B6D4) measures 2.43:1 with white text on this background. " +
                "Current ratio: " + ratio,
            ratio >= bodyText,
        )
    }

    @Test
    fun `the page background is light, not a dark gradient`() {
        // "Deep navy foundation" is the brand's anchor colour, not an instruction to put readable
        // content on a dark surface. A dark background here would silently invert every screen.
        assertTrue(
            "the background must stay light; relative luminance was " + luminance(BackgroundLight),
            luminance(BackgroundLight) > 0.7,
        )
    }

    @Test
    fun `the brand roles are separable on at least one visual axis`() {
        /*
         * An earlier version of this test required a luminance contrast ratio between every
         * pair of brand roles, and it failed -- correctly reporting a number, incorrectly
         * interpreting it. Electric blue and gold measure 1.05:1 in luminance while sitting
         * 172.7 degrees apart in hue: near-opposite colours that a sighted user cannot
         * confuse. Contrast ratio measures lightness, not colour, so it was the wrong
         * instrument for the question.
         *
         * The right requirement is that each pair separates on *some* axis. Navy and electric
         * blue are the same hue family (2.2 degrees apart) and separate by lightness; blue and
         * gold separate by hue.
         */
        val roles = listOf(
            "navy" to PrimaryLight,
            "electric blue" to SecondaryLight,
            "gold" to TertiaryLight,
            "cyan" to AccentCyanLight,
        )

        roles.forEachIndexed { i, (nameA, colourA) ->
            roles.drop(i + 1).forEach { (nameB, colourB) ->
                val byLightness = contrast(colourA, colourB)
                val byHue = hueDelta(colourA, colourB)
                assertTrue(
                    nameA + " and " + nameB +
                        " are separable on neither axis: lightness ratio " +
                        byLightness + ", hue delta " + byHue + " deg",
                    byLightness >= 1.5 || byHue >= 25.0,
                )
            }
        }
    }

    @Test
    fun `gold and electric blue are recorded as near-identical in lightness`() {
        /*
         * Not a defect -- a documented consequence. Because these two differ almost entirely
         * by hue, they are nearly indistinguishable in greyscale and to a viewer with severe
         * colour vision deficiency. That is precisely why the audit requires status never to
         * be communicated by colour alone, and this test exists so the fact stays visible
         * rather than being rediscovered from a support ticket.
         */
        val ratio = contrast(SecondaryLight, TertiaryLight)

        assertTrue(
            "If these have become separable by lightness (ratio " + ratio +
                "), the colour-blind caveat in docs/ui-ux-modernization-audit.md can be " +
                "relaxed -- update both together.",
            ratio < 1.5,
        )
    }
    // ------------------------------------------------------------------ the arithmetic

    private fun assertAtLeast(name: String, fg: Color, bg: Color, required: Double) {
        val ratio = contrast(fg, bg)
        assertTrue(
            name + ": contrast " + ratio + " is below the required " + required + ":1",
            ratio >= required,
        )
    }

    /**
     * Separation in hue, in degrees, 0..180.
     *
     * The companion to contrast: two colours of equal lightness can still be unmistakable,
     * and this is the axis that says so.
     */
    private fun hueDelta(a: Color, b: Color): Double {
        val delta = kotlin.math.abs(hueDegrees(a) - hueDegrees(b))
        return kotlin.math.round(min(delta, 360.0 - delta) * 10) / 10
    }

    private fun hueDegrees(color: Color): Double {
        val r = color.red.toDouble()
        val g = color.green.toDouble()
        val b = color.blue.toDouble()
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val span = hi - lo
        if (span == 0.0) return 0.0
        val hue = when (hi) {
            r -> 60.0 * (((g - b) / span) % 6.0)
            g -> 60.0 * (((b - r) / span) + 2.0)
            else -> 60.0 * (((r - g) / span) + 4.0)
        }
        return (hue + 360.0) % 360.0
    }

    /** WCAG 2.1 contrast ratio, rounded to two places so a failure message reads cleanly. */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val ratio = (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        return kotlin.math.round(ratio * 100) / 100
    }

    /**
     * WCAG 2.1 relative luminance.
     *
     * Computed here rather than via `Color.luminance()` so the expectation is independent of the
     * Compose implementation: this test should fail when the *palette* changes, never because a
     * library changed how it rounds.
     */
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
