package com.aiplacementos.app.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every screen a student can be pushed into has a visible way back.
 *
 * ## The measurement this came from
 *
 * A navigation audit on 2026-10-18 compared all 97 screen files against all 101 destinations in
 * the graph. **52 screens had no back affordance of any kind** -- no `navigationIcon`, no
 * `DetailTopBar`, no back control. The system back gesture still worked, so nobody was trapped,
 * but nothing on screen said so. On a gesture-navigation device, where there is no visible
 * system back button either, that is indistinguishable from being stuck.
 *
 * ## Why this is a source guard
 *
 * The property is "a student can see the way out of this destination", and no composed output
 * can observe it: the back control may come from the graph's wrapper, from a `DetailTopBar`
 * inside the screen, or from a `BackBar` in the screen's own `Scaffold`. What can be checked
 * exactly is that each destination uses *one* of those, and that the few that use none are the
 * ones deliberately chosen -- which is what [ROOTS] records.
 *
 * The honest limit: this proves the control is wired, not that it is reachable by touch or
 * correctly placed. That needs eyes on a device and is tracked as an open gate in
 * `docs/ui-ux-modernization-audit.md`.
 */
class BackAffordanceTest {

    /**
     * The destinations that legitimately have no back control, and why each one.
     *
     * This list is the point of the test. Any new destination is a failure until someone either
     * gives it a back control or adds it here with a reason -- which makes "this screen is a
     * root" a decision rather than an omission.
     */
    private val roots = mapOf(
        "Screen.Welcome.route" to
            "the first screen of an unauthenticated session; there is nothing behind it",
        "Screen.Main.route" to
            "the tab host. Its tabs are switched by MainScreen's own selectedRoute, not pushed, " +
                "so a back arrow here would leave the app rather than go up a level",
        "Screen.OnboardingName.route" to
            "the first step of onboarding, entered with popUpTo(0) after auth",
        "Screen.OnboardingSuccess.route" to
            "the end of onboarding. Back from here would return to the confirmation step of a " +
                "profile that has already been submitted",
    )

    @Test
    fun `every pushed destination offers a way back`() {
        val offenders = destinations()
            .filter { it.route !in roots.keys }
            .filter { !it.hasBack }
            .map { it.route + " -> " + it.screen }

        assertEquals(
            "These destinations are pushed onto the back stack and draw no back control, so a " +
                "student has no visible way out of them. Register them with backComposable(), " +
                "pass an onBack that pops, or -- if one is genuinely a root -- add it to " +
                "BackAffordanceTest.roots with the reason: " + offenders,
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the roots are all real destinations`() {
        // A stale entry here would silently excuse a destination that no longer exists while a
        // renamed one failed for a reason nobody could find.
        val known = destinations().map { it.route }.toSet()
        val ghosts = roots.keys.filterNot { it in known }

        assertEquals(
            "These are listed as back-less roots but are not destinations in the graph any " +
                "more. Remove them, or the list stops describing the app: " + ghosts,
            emptyList<String>(),
            ghosts,
        )
    }

    @Test
    fun `the back control is the mirroring arrow`() {
        // Icons.Filled.ArrowBack always points left. In a right-to-left locale -- Urdu, Arabic --
        // "back" is to the right, and the non-mirrored icon points away from where back is.
        val bar = source("ui/common/BackBar.kt")
        assertTrue(
            "BackBar must use Icons.AutoMirrored.Filled.ArrowBack so the arrow flips in " +
                "right-to-left locales.",
            bar.contains("AutoMirrored.Filled.ArrowBack"),
        )
        assertTrue(
            "The back control must carry a contentDescription; without one TalkBack announces " +
                "an unlabelled button and the only way out of the screen is unusable.",
            bar.contains("contentDescription = \"Back\""),
        )
    }

    @Test
    fun `the back control is not drawn over a screen that already has one`() {
        /*
         * Two back arrows on one screen is worse than none: the student cannot tell which is the
         * real one, and the lower of the two sits inside the content where it looks like part of
         * the page.
         *
         * A screen that brings its own DetailTopBar must therefore not also be wrapped, because
         * the wrapper adds a second bar above it.
         */
        val withOwnTopBar = destinations().filter { it.screenSource.contains("DetailTopBar(") }
        val doubled = withOwnTopBar.filter { it.builder == "backComposable" }
            .map { it.route + " -> " + it.screen }

        assertEquals(
            "These screens draw their own DetailTopBar, which already has a back arrow, and are " +
                "also registered with backComposable -- so they render two: " + doubled,
            emptyList<String>(),
            doubled,
        )
    }

    @Test
    fun `no screen prints its own title twice`() {
        /*
         * The rule the graph applies is one title per screen: `backComposable` is given a title
         * only for a screen that draws no heading of its own. Passing one to a screen that
         * already has a headline prints the same words twice, stacked.
         *
         * Checked by substring rather than by parsing the headline, because the duplication that
         * matters is literal -- the same string in the bar and in the content.
         */
        val doubled = destinations()
            .filter { it.title != null }
            .filter { it.screenSource.contains("\"" + it.title + "\"") }
            .map { it.screen + " already contains \"" + it.title + "\"" }

        assertEquals(
            "backComposable was given a title that the screen itself also renders, so it " +
                "appears twice: " + doubled,
            emptyList<String>(),
            doubled,
        )
    }

    // ------------------------------------------------------------------ reading the graph

    private data class Destination(
        val builder: String,
        val route: String,
        val screen: String,
        val title: String?,
        val hasBack: Boolean,
        val screenSource: String,
    )

    private fun destinations(): List<Destination> {
        val nav = source("navigation/NavGraph.kt")
        val at = nav.indexOf("NavHost(")
        assertTrue("could not find the NavHost in NavGraph.kt", at > 0)
        val graph = nav.substring(at)

        val starts = Regex("""\n {8}(safeAreaComposable|backComposable|composable)\(""")
            .findAll(graph).toList()
        // A guard that reads an empty graph passes for the wrong reason.
        assertTrue(
            "found " + starts.size + " destinations; the graph should hold the whole app",
            starts.size >= 95,
        )

        val screens = screenSources()
        return starts.mapIndexed { i, m ->
            val from = m.range.last + 1
            val to = if (i + 1 < starts.size) starts[i + 1].range.first else graph.length
            val block = graph.substring(from, to)
            val route = Regex("""Screen\.\w+\.route""").find(block)?.value ?: "?"
            val name = Regex("""\b([A-Z]\w*Screen)\s*\(""").find(block)?.groupValues?.get(1)
            Destination(
                builder = m.groupValues[1],
                route = route,
                screen = name ?: "?",
                // Only the title given to the wrapper, which is the one that could duplicate.
                title = Regex("""title = "([^"]+)"""").find(block.substringBefore("{"))
                    ?.groupValues?.get(1),
                // Three legitimate sources of a back control: the wrapper, an onBack passed to
                // the screen, or a popBackStack wired into the block itself.
                hasBack = m.groupValues[1] == "backComposable" ||
                    block.contains("onBack") ||
                    block.contains("onNavigateBack"),
                screenSource = screens[name] ?: "",
            )
        }
    }

    private fun screenSources(): Map<String, String> {
        val root = sourceRoot().resolve("ui")
        require(root.isDirectory) { "ui/ not found at " + root.absolutePath }
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.nameWithoutExtension.endsWith("Screen") }
            .associate { it.nameWithoutExtension to it.readText() }
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
