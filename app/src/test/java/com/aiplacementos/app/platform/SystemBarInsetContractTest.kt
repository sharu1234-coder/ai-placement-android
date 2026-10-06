package com.aiplacementos.app.platform

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 28, as a rule rather than a one-off fix: who owns the window insets in this app.
 *
 * The defect these guards exist for was never eight screens. `MainActivity` calls
 * `enableEdgeToEdge()`, so the app draws behind the status and navigation bars, and until Phase 28
 * the only thing in the entire codebase that consumed a window inset was `MainScreen`'s Scaffold.
 * That Scaffold covers the five bottom tabs it hosts directly; the other 70 of 98 NavHost
 * destinations are sibling entries that inherited nothing and began drawing at y=0, under the
 * clock. Phase 27 measured eight of them because eight is how many it happened to visit.
 *
 * The fix is one shared contract in the navigation layer, which means the thing worth guarding is
 * the contract, not the eight screens. Two failure shapes matter:
 *
 *  * a new destination is added on plain `composable` and silently draws under the status bar --
 *    the original defect, re-entering through the door it came in by;
 *  * the padding stops coming from the system. A hard-coded `48.dp` looks right on the device it
 *    was measured on and is wrong on the next one, and it cannot know about a display cutout, a
 *    large-screen taskbar or the keyboard.
 *
 * So these read the real navigation graph and the real screen sources rather than a list of screen
 * names, and they fail when the contract is broken anywhere -- including on screens that did not
 * exist when it was written.
 */
class SystemBarInsetContractTest {

    private fun moduleRoot(): File {
        val candidates = listOf(File("."), File("app"), File("../app"))
        val root = candidates.firstOrNull { File(it, "build.gradle.kts").isFile }
        requireNotNull(root) {
            "Could not locate the app module from ${File(".").absolutePath}; these guards would " +
                "otherwise pass by reading nothing."
        }
        return root
    }

    private fun mainSources(): List<File> {
        val root = File(moduleRoot(), "src/main/java/com/aiplacementos/app")
        require(root.isDirectory) { "no production sources at ${root.absolutePath}" }
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("scanned no production sources", files.size > 100)
        return files
    }

    private fun navGraph(): String =
        File(moduleRoot(), "src/main/java/com/aiplacementos/app/navigation/NavGraph.kt")
            .also { require(it.isFile) { "no NavGraph.kt at ${it.absolutePath}" } }
            .readText()

    private fun mainScreen(): String =
        File(moduleRoot(), "src/main/java/com/aiplacementos/app/navigation/MainScreen.kt")
            .also { require(it.isFile) { "no MainScreen.kt at ${it.absolutePath}" } }
            .readText()

    /**
     * The same text with whole-line comments removed, for guards that require something to be
     * *present*.
     *
     * Phase 27 learned this the hard way: a mutation that commented out `enableEdgeToEdge()` was
     * missed, because the call was still in the source as a comment and a plain `contains` could
     * not tell the difference. Only whole-line comments are dropped -- stripping trailing `//`
     * would cut a `"https://..."` literal in half.
     */
    private fun codeOnly(text: String): String =
        text.lines()
            .filterNot { line ->
                val t = line.trimStart()
                t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
            }
            .joinToString("\n")

    /** Source text of every top-level composable in the app, keyed by function name. */
    private fun composableBodies(): Map<String, List<String>> {
        val decl = Regex("""^fun\s+([A-Z]\w*)\s*\(""", RegexOption.MULTILINE)
        val next = Regex("""^(@|private fun |internal fun |fun |class |object )""", RegexOption.MULTILINE)
        val out = mutableMapOf<String, MutableList<String>>()
        for (file in mainSources()) {
            val text = file.readText()
            for (m in decl.findAll(text)) {
                val after = text.substring(m.range.last + 1)
                val end = next.find(after)?.let { m.range.last + 1 + it.range.first } ?: text.length
                out.getOrPut(m.groupValues[1]) { mutableListOf() }.add(text.substring(m.range.first, end))
            }
        }
        assertTrue("resolved no composables", out.size > 100)
        return out
    }

    private data class Destination(val builder: String, val route: String, val screen: String) {
        /**
         * Whether the screen this destination renders brings its own Scaffold, and therefore its
         * own inset handling. This is the discriminator the Phase 28 fix is built on, and it is
         * the same one Phase 27's device measurements confirmed: every screen measured as drawing
         * under the status bar has no Scaffold, and every screen measured as clean has one or
         * happens to start its content below the bar.
         */
        var ownsScaffold: Boolean = false
    }

    private fun destinations(): List<Destination> {
        val src = navGraph()
        val navAt = src.indexOf("NavHost(navController")
        assertTrue("could not find the NavHost in NavGraph.kt", navAt > 0)
        val nav = src.substring(navAt)
        val starts = Regex("""\n {8}(safeAreaComposable|composable)\(""").findAll(nav).toList()
        assertTrue("found ${starts.size} destinations, expected the whole graph", starts.size > 90)
        val bodies = composableBodies()

        return starts.mapIndexed { i, m ->
            val from = m.range.last + 1
            val to = if (i + 1 < starts.size) starts[i + 1].range.first else nav.length
            val block = nav.substring(from, to)
            val route = block.substringBefore(")").substringBefore(",").trim()
            val named = Regex("""\b([A-Z]\w*)\s*\(""").findAll(block).map { it.groupValues[1] }.toList()
            // Screens are named *Screen by convention; the fallback exists so a destination that
            // breaks the convention is still resolved rather than silently skipped.
            val screen = named.firstOrNull { it.endsWith("Screen") && bodies.containsKey(it) }
                ?: named.firstOrNull { bodies.containsKey(it) }
            requireNotNull(screen) { "could not resolve the screen composable for $route" }
            Destination(m.groupValues[1], route, screen).apply {
                ownsScaffold = bodies.getValue(screen).any { it.contains("Scaffold(") }
            }
        }
    }

    /** The body of the shared contract helper, which is what most of these guards read. */
    private fun sharedContract(): String {
        val src = navGraph()
        val at = src.indexOf("private fun NavGraphBuilder.safeAreaComposable")
        assertTrue(
            "The shared inset contract is gone from NavGraph.kt. Every bar-less destination " +
                "depends on it to stay inside the safe area.",
            at > 0,
        )
        return codeOnly(src.substring(at, minOf(at + 900, src.length)))
    }

    @Test
    fun `every destination without a scaffold goes through the shared inset contract`() {
        val offenders = destinations()
            .filter { !it.ownsScaffold && it.builder != "safeAreaComposable" }
            .map { "${it.route} -> ${it.screen}" }
        assertEquals(
            "These destinations render a screen that has no Scaffold of its own, yet are " +
                "registered with plain composable(), so nothing puts their content inside the " +
                "safe area and they will draw under the status bar: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `every destination with a scaffold is left to handle its own insets`() {
        val offenders = destinations()
            .filter { it.ownsScaffold && it.builder == "safeAreaComposable" }
            .map { "${it.route} -> ${it.screen}" }
        assertEquals(
            "These destinations own a Scaffold, which already applies the content insets, and " +
                "are also wrapped in the shared contract. Their top and bottom bars are supposed " +
                "to draw behind the system bars; padding above them pulls those bars down and " +
                "leaves a strip of bare window behind: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the shared contract takes its padding from the system`() {
        val body = sharedContract()
        assertTrue(
            "The shared contract no longer applies safeDrawingPadding, so nothing derives the " +
                "padding from the system's own inset report.",
            body.contains("safeDrawingPadding()"),
        )
    }

    @Test
    fun `the shared contract hard codes no device measurement`() {
        val body = sharedContract()
        val literals = Regex("""\b\d+\.dp\b""").findAll(body).map { it.value }.toList()
        assertEquals(
            "The shared contract contains a fixed dp value $literals. A measured constant is " +
                "right on one device and wrong on the next, and it cannot know about a display " +
                "cutout, a large-screen taskbar or the keyboard.",
            emptyList<String>(),
            literals,
        )
    }

    @Test
    fun `the shared contract applies exactly one inset padding`() {
        val body = sharedContract()
        val applied = Regex(
            """\b(safeDrawingPadding|safeContentPadding|systemBarsPadding|statusBarsPadding"""
                + """|navigationBarsPadding|imePadding|windowInsetsPadding)\(""",
        ).findAll(body).map { it.groupValues[1] }.toList()
        assertEquals(
            "The contract must apply one inset padding, not $applied. Two of them in the same " +
                "chain pad the content twice -- the status bar's height taken out of the layout " +
                "again, or the gesture bar's -- which is the double-spacing failure that makes " +
                "a fix look worse than the defect.",
            1,
            applied.size,
        )
    }

    @Test
    fun `the shared contract covers every edge including the keyboard`() {
        val body = sharedContract()
        assertTrue(
            "The shared contract narrows safeDrawing with .only(...), which drops at least one " +
                "edge. safeDrawing is the union of the system bars, the display cutout and the " +
                "IME; restricting it is how the bottom overlap or the keyboard case comes back.",
            !body.contains(".only("),
        )
    }

    @Test
    fun `the shared contract is not conditional`() {
        val body = sharedContract()
        val gates = listOf("Configuration", "orientation", "ORIENTATION_", "isLandscape", "screenWidthDp")
            .filter { body.contains(it) }
        assertEquals(
            "The shared contract branches on $gates. Insets are already orientation- and " +
                "size-dependent because the system reports them per configuration; gating the " +
                "contract on one of those is how landscape or a large screen regresses while " +
                "portrait keeps passing.",
            emptyList<String>(),
            gates,
        )
    }

    @Test
    fun `no screen applies a system bar inset of its own`() {
        val own = listOf(
            "statusBarsPadding", "navigationBarsPadding", "systemBarsPadding",
            "safeDrawingPadding", "safeContentPadding", "windowInsetsPadding",
        )
        val offenders = mainSources()
            .filter { it.name != "NavGraph.kt" }
            .flatMap { file ->
                val text = codeOnly(file.readText())
                own.filter { text.contains("$it(") }.map { "${file.name}: $it" }
            }
        assertEquals(
            "The navigation layer owns the system-bar inset for every bar-less destination, and " +
                "it consumes what it applies. A screen that also applies one is either " +
                "double-padding its content or guessing an inset the container already handled: " +
                "$offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `no screen hard codes a system bar sized padding`() {
        // Nothing in this app padded by a raw dp value above single digits before Phase 28, so a
        // bar-sized constant appearing anywhere means someone measured a device and typed it in.
        val suspicious = Regex("""padding\([^)]*?\b([2-9]\d|\d{3,})\.dp""")
        val offenders = mainSources().flatMap { file ->
            suspicious.findAll(codeOnly(file.readText())).map { "${file.name}: ${it.value.trim()}" }
        }
        assertEquals(
            "These look like a system bar measured off one device and hard-coded. The inset has " +
                "to come from the system: it differs per device, per orientation, with a cutout, " +
                "with a large-screen taskbar and with the keyboard up: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the bottom navigation still consumes the system navigation bar inset`() {
        val src = codeOnly(mainScreen())
        assertTrue("MainScreen no longer has a NavigationBar", src.contains("NavigationBar {"))
        assertTrue(
            "MainScreen's NavigationBar has been given an explicit windowInsets. Its default is " +
                "the system bars on the bottom and horizontal edges, which is what keeps the tab " +
                "labels clear of the gesture bar and of a large-screen taskbar; overriding it " +
                "with zero is exactly the large-screen overlap Phase 27 measured at 720dp.",
            !Regex("""NavigationBar\s*\(\s*[^)]*windowInsets""").containsMatchIn(src),
        )
    }

    @Test
    fun `the main scaffold still passes its content padding to the tabs`() {
        val src = codeOnly(mainScreen())
        assertTrue(
            "MainScreen's Scaffold no longer forwards innerPadding to its content, so the five " +
                "bottom tabs would render under its own top bar and bottom navigation.",
            src.contains("padding(innerPadding)"),
        )
        assertTrue(
            "MainScreen's Scaffold has been given an explicit contentWindowInsets. It is the " +
                "only inset consumer for the five tabs it hosts.",
            !src.contains("contentWindowInsets"),
        )
    }

    @Test
    fun `edge to edge is still enabled and nothing opts out`() {
        val activity = File(moduleRoot(), "src/main/java/com/aiplacementos/app/MainActivity.kt")
        assertTrue(
            "MainActivity no longer calls enableEdgeToEdge(). The inset contract exists because " +
                "the app draws behind the system bars; without it the padding is doubled by the " +
                "platform fitting the window instead.",
            codeOnly(activity.readText()).contains("enableEdgeToEdge()"),
        )
        val optOuts = File(moduleRoot(), "src").walkTopDown()
            .filter { it.isFile && it.extension == "xml" }
            .filter { it.readText().contains("windowOptOutEdgeToEdgeEnforcement") }
            .map { it.name }
            .toList()
        assertEquals("something opts out of edge-to-edge enforcement", emptyList<String>(), optOuts)
    }

    @Test
    fun `the inset contract is declared exactly once`() {
        val src = navGraph()
        assertEquals(
            "The shared contract must be declared once; a second copy is how two of them end up " +
                "nested and the padding is applied twice.",
            1,
            Regex("""private fun NavGraphBuilder\.safeAreaComposable""").findAll(src).count(),
        )
        assertEquals(
            "The contract body must wrap its content once. A nested second wrapper would pad " +
                "every bar-less screen twice.",
            1,
            Regex("""safeDrawingPadding\(\)""").findAll(src).count(),
        )
    }

    @Test
    fun `the whole graph is accounted for`() {
        val all = destinations()
        val wrapped = all.count { it.builder == "safeAreaComposable" }
        val plain = all.count { it.builder == "composable" }
        assertEquals("every destination is either wrapped or deliberately plain", all.size, wrapped + plain)
        assertEquals(
            "Destinations whose screen has no Scaffold must be exactly the wrapped ones.",
            all.count { !it.ownsScaffold },
            wrapped,
        )
        assertTrue(
            "The graph should still hold the whole app; reading ${all.size} destinations means " +
                "this analysis stopped early and the guards above checked almost nothing.",
            all.size >= 95,
        )
    }

    @Test
    fun `the phase 27 target api is untouched`() {
        val build = codeOnly(File(moduleRoot(), "build.gradle.kts").readText())
        assertTrue("targetSdk is no longer 36", build.contains("targetSdk = 36"))
        assertTrue("compileSdk is no longer 36", build.contains("compileSdk = 36"))
    }
}
