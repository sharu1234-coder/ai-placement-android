package com.aiplacementos.app.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The design system is already disciplined. These guards keep it that way.
 *
 * A product-wide UI audit on 2026-10-18 measured the actual drift across all 97 screen files and
 * found, against expectation:
 *
 * - **0** hardcoded `Color(0xFF...)` literals outside `ui/theme/`.
 * - **0** arbitrary `fontSize` values outside `Typography.kt`.
 * - **973** uses of the `Spacing` scale across 116 files.
 *
 * In other words the colour and type systems are not drifting; they are centralised and every
 * screen already goes through them. That is a property worth *keeping*, and it is the kind of
 * property that decays one well-meaning `Color(0xFF1A237E)` at a time. These tests make the next
 * such edit fail here, with an explanation, rather than quietly becoming the eighth shade of blue.
 *
 * They are source guards rather than runtime assertions because the rule is about where a value
 * may be written, which no composed output can observe.
 */
class DesignSystemGuardTest {

    // ------------------------------------------------------------------ colour

    @Test
    fun `colour literals live only in the theme`() {
        val offenders = uiSources()
            .filterNot { it.path.replace('\\', '/').contains("/ui/theme/") }
            .filter { it.readText().contains("Color(0xFF") }
            .map { it.name }

        assertEquals(
            "Colour must come from MaterialTheme.colorScheme or ui/theme/, so the palette can be " +
                "changed in one place. A literal here is a shade nobody can find later. " +
                "Offenders: " + offenders,
            emptyList<String>(),
            offenders,
        )
    }

    // ------------------------------------------------------------------ typography

    @Test
    fun `font sizes live only in the type scale`() {
        val fontSize = Regex("""fontSize\s*=\s*\d""")
        val offenders = uiSources()
            .filterNot { it.name == "Typography.kt" }
            .filter { fontSize.containsMatchIn(it.readText()) }
            .map { it.name }

        assertEquals(
            "Text styling must come from MaterialTheme.typography. An inline fontSize is how a " +
                "screen ends up one step off every other screen, and it breaks font scaling for " +
                "anyone who has enlarged their system text. Offenders: " + offenders,
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the type scale covers every role a screen needs`() {
        // Asserted as a set: a role quietly dropped would send callers back to inline styling,
        // which is exactly what the guard above forbids.
        val typography = source("ui/theme/Typography.kt")
        val required = listOf(
            "headlineLarge", "headlineMedium", "headlineSmall",
            "titleLarge", "titleMedium", "titleSmall",
            "bodyLarge", "bodyMedium", "bodySmall",
            "labelLarge", "labelMedium", "labelSmall",
        )
        val missing = required.filterNot { typography.contains(it + " =") }

        assertEquals("type roles missing from the scale: " + missing, emptyList<String>(), missing)
    }

    @Test
    fun `every type style declares a line height`() {
        // A style without one inherits a platform default that does not scale with the size, so
        // large-font users get overlapping text.
        val typography = source("ui/theme/Typography.kt")
        val styles = Regex("""TextStyle\([^)]*\)""").findAll(typography).map { it.value }.toList()

        assertTrue("no TextStyle declarations found -- this guard must not scan nothing", styles.isNotEmpty())
        val without = styles.filterNot { it.contains("lineHeight") }
        assertEquals("styles with no lineHeight: " + without, emptyList<String>(), without)
    }

    // ------------------------------------------------------------------ spacing

    @Test
    fun `the spacing scale exists and is actually used`() {
        val spacing = source("ui/theme/Spacing.kt")
        listOf("sm", "md", "lg").forEach { token ->
            assertTrue(
                "Spacing." + token + " must exist; 116 files already depend on this scale",
                spacing.contains("val " + token + " ="),
            )
        }

        val users = uiSources().count { it.readText().contains("Spacing.") }
        // Measured at 116 on 2026-10-18. Asserted as a floor, not an equality: new screens should
        // push this up, and a sharp drop means a screen started hardcoding its own padding.
        assertTrue(
            "only " + users + " files use the Spacing scale; it was 116 at the audit, so " +
                "something has started hardcoding padding instead",
            users >= 110,
        )
    }

    // ------------------------------------------------------------------ the theme itself

    @Test
    fun `the theme supplies colour, type and shape together`() {
        val theme = source("ui/theme/Theme.kt")

        assertTrue("the theme must supply the colour scheme", theme.contains("colorScheme ="))
        assertTrue("the theme must supply the type scale", theme.contains("typography = AppTypography"))
        // Shapes too: a MaterialTheme without them falls back to Material defaults, which is how
        // button and card corners drift apart between screens.
        assertTrue("the theme must supply the shape scale", theme.contains("shapes = AppShapes"))
    }

    @Test
    fun `the single-theme decision is recorded in the source that implements it`() {
        /*
         * Light-only is a deliberate product decision, not an omission: the comment in Theme.kt
         * says dark mode is "deliberately not offered, regardless of the device's system
         * setting".
         *
         * This guard exists because the decision is *only* recorded in a code comment. The
         * document it cites, UI_UX_REFERENCES.md, is not in this repository, so the comment is
         * the sole surviving record. If someone adds a dark scheme, this test should fail and
         * force the decision to be taken deliberately rather than inherited from a sample app.
         */
        val theme = source("ui/theme/Theme.kt")

        assertTrue(
            "The light-only decision must stay documented where it is implemented; it is the " +
                "only record of it that exists.",
            theme.contains("deliberately not offered"),
        )
        assertTrue(
            "Adding darkColorScheme is a product decision about brand identity, not a styling " +
                "tweak. If that decision has been taken, update this guard and the audit doc " +
                "(docs/ui-ux-modernization-audit.md) in the same change.",
            !theme.contains("darkColorScheme"),
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun uiSources(): List<File> {
        val root = sourceRoot().resolve("ui")
        require(root.isDirectory) { "ui/ not found at " + root.absolutePath }
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        // A guard that scans nothing passes for the wrong reason.
        require(files.size > 50) { "expected the whole ui tree, found " + files.size + " files" }
        return files
    }

    private fun source(relative: String): String {
        val file = sourceRoot().resolve(relative)
        require(file.isFile) { "could not read " + file.absolutePath }
        return file.readText()
    }

    private fun sourceRoot(): File {
        val base = "src/main/java/com/aiplacementos/app"
        return listOf(File(base), File("app/" + base), File("../app/" + base))
            .firstOrNull { it.isDirectory }
            ?: error("could not locate the source root from " + File(".").absolutePath)
    }
}
