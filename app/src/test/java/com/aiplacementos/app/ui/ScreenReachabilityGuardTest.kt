package com.aiplacementos.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two defects that were present on dozens of screens at once, and the guards that stop them
 * coming back.
 *
 * Both were found by scanning the whole `ui` tree rather than by reading screens one at a
 * time, which is the only reason they were found at all: each looks like a local styling
 * choice in its own file and only reads as a defect when you see it twenty times.
 */
class ScreenReachabilityGuardTest {

    /**
     * **The content must be reachable.**
     *
     * A `Column(Modifier.fillMaxSize())` lays its children out once and clips whatever does
     * not fit. At a raised system font scale, or with the soft keyboard open, that routinely
     * puts a screen's primary button off the bottom edge with no way to scroll to it -- on
     * `TestTakingScreen` it meant a student could read a question and then be unable to reach
     * Submit.
     *
     * A screen satisfies this by scrolling, by using a lazy list, or by handing the remaining
     * space to a child with `weight`, which bounds the region that needs to scroll.
     */
    @Test
    fun `every full-screen composable can scroll or delegates its height`() {
        val offenders = screenSources()
            .filter { file ->
                val text = file.readText()
                val claimsWholeScreen = text.contains("fillMaxSize()")
                val canReachBottom = SCROLL_MARKERS.any { text.contains(it) }
                claimsWholeScreen && !canReachBottom
            }
            .map { it.name }

        assertEquals(
            "These screens fill the window with a non-scrolling container, so content past " +
                "the fold -- usually the primary button -- cannot be reached at a large font " +
                "scale or with the keyboard open. Give the container " +
                "verticalScroll(rememberScrollState()), use a lazy list, or bound it with " +
                "weight(). Offenders: " + offenders,
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * **Working must look like working.**
     *
     * `text = if (busy) "Saving..." else "Save"` with `enabled = !busy` leaves a disabled
     * button and a changed word as the only evidence the app is doing anything, which is
     * indistinguishable from a button that did nothing. Both shared buttons take `loading`,
     * which shows a spinner *and* blocks a second submit, so the two cannot drift apart.
     */
    @Test
    fun `buttons show progress with the loading parameter, not by rewording themselves`() {
        // Scoped to the inside of a button call, not to the file. An earlier draft matched
        // any `text = if (busy) "..." else`, which flagged TestCenterScreen's category tile
        // -- a Text that says "Starting..." *beside* a spinner. That is the opposite of this
        // defect: the spinner is present and the words add detail. The rule is about buttons
        // whose reworded label is the only signal, so the guard has to look at buttons.
        val reworded = Regex("""text = if \([^)]*\) "[^"]*\.\.\." else""")
        val buttonCall = Regex("""(Primary|Secondary)Button\(\n(.*?)\n\s*\)""", RegexOption.DOT_MATCHES_ALL)
        val offenders = screenSources()
            .filter { file ->
                buttonCall.findAll(file.readText()).any { reworded.containsMatchIn(it.groupValues[2]) }
            }
            .map { it.name }

        assertEquals(
            "These screens signal a busy action by changing the button's label instead of " +
                "passing loading = true. Use PrimaryButton/SecondaryButton's loading " +
                "parameter. Offenders: " + offenders,
            emptyList<String>(),
            offenders,
        )
    }

    /** Both shared buttons must actually offer the parameter the guard above insists on. */
    @Test
    fun `both shared buttons support a loading state`() {
        val buttons = File(sourceRoot(), "ui/common/Buttons.kt").readText()
        listOf("fun PrimaryButton", "fun SecondaryButton").forEach { declaration ->
            val body = buttons.substringAfter(declaration).substringBefore("\n}")
            assertTrue(
                "$declaration must accept loading: Boolean so callers never need to reword a " +
                    "label to show progress.",
                body.contains("loading: Boolean"),
            )
            assertTrue(
                "$declaration must disable itself while loading, otherwise it still permits " +
                    "a double submit.",
                body.contains("!loading"),
            )
        }
    }

    private companion object {
        /** Any one of these means the screen's content can be reached. */
        val SCROLL_MARKERS = listOf(
            "verticalScroll", "LazyColumn", "LazyVerticalGrid", "LazyRow", "weight(1f)",
        )
    }

    private fun screenSources(): List<File> {
        val root = sourceRoot().resolve("ui")
        require(root.isDirectory) { "ui/ not found at " + root.absolutePath }
        val files = root.walkTopDown()
            .filter { it.isFile && it.name.endsWith("Screen.kt") }
            .toList()
        // A guard that scans nothing passes for the wrong reason.
        require(files.size > 50) { "expected the whole screen set, found " + files.size }
        return files
    }

    private fun sourceRoot(): File {
        val base = "src/main/java/com/aiplacementos/app"
        return listOf(File(base), File("app/" + base), File("../app/" + base))
            .firstOrNull { it.isDirectory }
            ?: error("could not locate the source root from " + File(".").absolutePath)
    }
}
