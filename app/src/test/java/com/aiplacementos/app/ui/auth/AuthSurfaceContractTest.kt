package com.aiplacementos.app.ui.auth

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards for the three defects the authentication review found.
 *
 * All three were present on every auth screen at once, which is the signature of a pattern
 * that was copied rather than shared. Source guards rather than runtime assertions, for the
 * same reason as `DesignSystemGuardTest`: the rule is about where a thing may be written,
 * which no composed output can observe.
 */
class AuthSurfaceContractTest {

    /**
     * The defect: Login, Signup and Forgot Password each built their own password input with
     * `PasswordVisualTransformation()` and **no way to reveal what was typed**. On a phone
     * keyboard that is the most common reason a correct password is reported as rejected.
     *
     * The fix has to live in one component or the next screen that needs a password will be
     * missing it again, so this guard is on the location, not on the behaviour.
     */
    @Test
    fun `password input goes through the shared field, which can reveal what was typed`() {
        val shared = source("ui/common/Fields.kt")
        assertTrue(
            "PasswordField must offer a reveal toggle; without it the shared component " +
                "reintroduces the defect it exists to fix.",
            shared.contains("VisualTransformation.None") && shared.contains("Show password"),
        )

        val offenders = uiSources()
            .filterNot { it.name == "Fields.kt" }
            .filter { it.readText().contains("PasswordVisualTransformation") }
            .map { it.name }

        assertEquals(
            "A screen is building its own password input instead of using PasswordField, so " +
                "it has no reveal toggle. Offenders: " + offenders,
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * The defect: the welcome screen -- the first thing anyone sees -- showed a 96dp stock
     * rocket glyph in place of the brand, under the tagline "Your Personal Placement
     * Partner", which is not the approved one.
     */
    @Test
    fun `the welcome screen shows the brand and the approved tagline`() {
        val welcome = source("ui/auth/WelcomeScreen.kt")

        assertTrue(
            "WelcomeScreen no longer renders the wordmark. A stock icon in place of the brand " +
                "is the first impression the product makes.",
            welcome.contains("BrandMark("),
        )
        assertTrue(
            "WelcomeScreen must take the tagline from PRODUCT_TAGLINE rather than spelling one " +
                "out, so the approved wording cannot drift screen by screen.",
            welcome.contains("PRODUCT_TAGLINE"),
        )
        assertEquals(
            "The approved tagline is fixed by the brand and must not be edited here.",
            "Your Potential, Accelerated.",
            Regex("""PRODUCT_TAGLINE\s*=\s*"([^"]+)"""")
                .find(source("ui/common/BrandMark.kt"))
                ?.groupValues
                ?.get(1),
        )
    }

    /**
     * The defect: none of the auth screens scrolled. On a short handset the soft keyboard
     * covers the password field and the submit button, with no way to bring them back.
     */
    @Test
    fun `every auth screen can scroll out from under the keyboard`() {
        val screens = listOf("WelcomeScreen", "LoginScreen", "SignupScreen")
        val offenders = screens.filterNot {
            source("ui/auth/$it.kt").contains("verticalScroll(")
        }

        assertEquals(
            "These auth screens do not scroll, so the soft keyboard can cover their submit " +
                "button with no way to reach it: " + offenders,
            emptyList<String>(),
            offenders,
        )
    }

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
