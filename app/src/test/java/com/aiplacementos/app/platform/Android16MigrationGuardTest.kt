package com.aiplacementos.app.platform

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 27, as rules rather than a one-off migration note: the things that made raising targetSdk
 * to 36 safe have to stay true after it.
 *
 * A target API bump is not a change you can regression-test by running a screen, because what it
 * changes is what the platform does around the app. Three of the four Android 16 behaviour changes
 * that could have reached this app were no-ops only because of something absent from the source --
 * no edge-to-edge opt-out, no fixed-orientation declaration, no back interception that predictive
 * back would stop calling. Absences are exactly what regresses silently: nothing fails when
 * someone adds one back, and the failure shows up later as a layout under the status bar on a
 * device nobody tested, or an alarm a student can dismiss with Back.
 *
 * So each test here pins one absence, or one presence the audit depended on. None of them is a
 * substitute for device testing; they are what stops the device testing from going stale.
 */
class Android16MigrationGuardTest {

    private fun moduleRoot(): File {
        val candidates = listOf(File("."), File("app"), File("../app"))
        val root = candidates.firstOrNull { File(it, "build.gradle.kts").isFile }
        requireNotNull(root) {
            "Could not locate the app module from ${File(".").absolutePath}; these guards would " +
                "otherwise pass by reading nothing."
        }
        return root
    }

    private fun buildScript(): String = File(moduleRoot(), "build.gradle.kts").readText()

    private fun manifest(flavor: String): String {
        val file = File(moduleRoot(), "src/$flavor/AndroidManifest.xml")
        require(file.isFile) { "no AndroidManifest.xml for flavor $flavor" }
        return file.readText()
    }

    private fun productionSources(): List<File> {
        val root = File(moduleRoot(), "src/main/java/com/aiplacementos/app")
        require(root.isDirectory) { "no production sources at ${root.absolutePath}" }
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("scanned no production sources", files.size > 100)
        return files
    }

    /** Every manifest and resource file the app ships, across both flavours. */
    private fun androidXml(): List<File> =
        File(moduleRoot(), "src").walkTopDown().filter { it.isFile && it.extension == "xml" }
            .toList()

    /**
     * The same text with whole-line comments removed, for the guards that require something to be
     * *present*.
     *
     * This exists because the first version of this file did not have it, and a mutation that
     * commented out `enableEdgeToEdge()` passed: the call was still in the source, as a comment,
     * and a plain `contains` could not tell the difference. That is the failure shape worth being
     * careful about -- not a guard that breaks, but one that keeps reporting success after the
     * thing it guards has been switched off.
     *
     * Only whole-line comments are dropped. Trailing `//` is left alone on purpose: stripping it
     * would cut `"https://..."` in half and break the URL checks below.
     */
    private fun codeOnly(text: String): String =
        text.lines()
            .filterNot { line ->
                val t = line.trimStart()
                t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
            }
            .joinToString("\n")

    // ---------------------------------------------------------------- the bump itself

    @Test
    fun `the app targets api 36`() {
        assertTrue(
            "targetSdk must stay 36: Play requires API 36 for new apps and for updates from " +
                "31 August 2026, and dropping back would silently un-opt the app from the " +
                "Android 16 behaviour this phase validated on a device.",
            Regex("""targetSdk\s*=\s*36""").containsMatchIn(codeOnly(buildScript())),
        )
    }

    @Test
    fun `the app compiles against api 36`() {
        assertTrue(
            "compileSdk must stay 36; firebase-bom Crashlytics references " +
                "android.os.ProfilingTrigger, which does not exist below it, and R8 fails the " +
                "release build without it.",
            Regex("""compileSdk\s*=\s*36""").containsMatchIn(codeOnly(buildScript())),
        )
    }

    @Test
    fun `the migration did not raise the floor`() {
        assertTrue(
            "minSdk must stay 26. Raising it is the tempting way to make a compatibility " +
                "problem disappear, and it does that by dropping devices rather than fixing code.",
            Regex("""minSdk\s*=\s*26""").containsMatchIn(codeOnly(buildScript())),
        )
    }

    // ---------------------------------------------------------------- edge to edge

    @Test
    fun `nothing opts out of edge to edge enforcement`() {
        val offenders = androidXml()
            .filter { it.readText().contains("windowOptOutEdgeToEdgeEnforcement") }
        assertEquals(
            "windowOptOutEdgeToEdgeEnforcement is deprecated and ignored for API 36 targets, so " +
                "adding it does not restore the old insets -- it only hides the problem on " +
                "Android 15 while the app still draws under the bars on Android 16.",
            emptyList<String>(), offenders.map { it.name },
        )
    }

    @Test
    fun `the activity still enables edge to edge explicitly`() {
        val main = codeOnly(productionSources().single { it.name == "MainActivity.kt" }.readText())
        assertTrue(
            "MainActivity must keep calling enableEdgeToEdge(). At API 36 the platform enforces " +
                "edge-to-edge regardless, so removing the call does not opt out -- it removes " +
                "the one place that states the intent and configures the system bars.",
            main.contains("enableEdgeToEdge()"),
        )
    }

    @Test
    fun `every scaffold consumes its content padding`() {
        val lambdaParam = Regex("""\)\s*\{\s*(\w+)\s*->""")
        val offenders = productionSources()
            .filter { it.readText().contains("Scaffold(") }
            .filter { file ->
                val text = file.readText()
                val bound = lambdaParam.findAll(text).map { it.groupValues[1] }.toSet()
                bound.none { text.contains("padding($it)") }
            }
        assertEquals(
            "A Scaffold whose content-padding parameter is never applied draws its content under " +
                "the status bar, the navigation bar, or its own app bar. This is the whole of the " +
                "edge-to-edge handling in this app -- there is no inset modifier anywhere else -- " +
                "so an unapplied padding is the exact Android 16 failure, not a style point.",
            emptyList<String>(), offenders.map { it.name },
        )
    }

    // ---------------------------------------------------------------- predictive back

    @Test
    fun `predictive back is never disabled`() {
        val offenders = androidXml().filter { file ->
            Regex("""enableOnBackInvokedCallback\s*=\s*"false"""").containsMatchIn(file.readText())
        }
        assertEquals(
            "enableOnBackInvokedCallback=false is the documented opt-out from predictive back, " +
                "and setting it would make this phase device testing describe an app that no " +
                "longer exists. The app needs no opt-out: its only back interception is a " +
                "BackHandler, which routes through OnBackPressedDispatcher and keeps working.",
            emptyList<String>(), offenders.map { it.name },
        )
    }

    @Test
    fun `no deprecated back interception is reintroduced`() {
        val offenders = productionSources().filter { file ->
            val text = file.readText()
            Regex("""override\s+fun\s+onBackPressed\s*\(""").containsMatchIn(text) ||
                text.contains("KEYCODE_BACK")
        }
        assertEquals(
            "For API 36 targets the platform no longer calls onBackPressed() and no longer " +
                "dispatches KEYCODE_BACK, so either one is dead code that silently stops " +
                "running -- the dangerous shape, because the handler looks present while the " +
                "behaviour it guarded is gone. Use BackHandler or PredictiveBackHandler.",
            emptyList<String>(), offenders.map { it.name },
        )
    }

    @Test
    fun `the alarm challenge still refuses back`() {
        val screen = productionSources()
            .single { it.name == "PlacementAlarmChallengeScreen.kt" }.readText()
        assertTrue(
            "The accountability alarm product requirement is that Back must not silence it. That " +
                "is enforced by one enabled BackHandler, and predictive back does not weaken it " +
                "-- an enabled callback still consumes the gesture rather than animating away.",
            Regex("""BackHandler\s*\(\s*enabled\s*=\s*true\s*\)""").containsMatchIn(codeOnly(screen)),
        )
    }

    // ---------------------------------------------------------------- large screens

    @Test
    fun `nothing declares a fixed orientation or aspect ratio`() {
        val offenders = androidXml().filter { file ->
            val text = file.readText()
            listOf("screenOrientation", "minAspectRatio", "maxAspectRatio", "resizableActivity")
                .any { text.contains(it) }
        }
        assertEquals(
            "At API 36 these are ignored on displays 600dp and wider, so declaring one buys a " +
                "false sense of control: the app would be written as though it were portrait-" +
                "locked while the platform rotates and resizes it anyway.",
            emptyList<String>(), offenders.map { it.name },
        )
    }

    @Test
    fun `no large screen compatibility opt out is smuggled in`() {
        val offenders = androidXml()
            .filter { it.readText().contains("PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY") }
        assertEquals(
            "This property opts the app out of Android 16 adaptive-layout behaviour. It is " +
                "available, and this app has no use for it: it declares no orientation or " +
                "aspect-ratio restriction for the platform to ignore. Adding it would be a " +
                "compatibility hack with nothing to be compatible with.",
            emptyList<String>(), offenders.map { it.name },
        )
    }

    // ---------------------------------------------------------------- what must not regress

    @Test
    fun `there is still exactly one retry exemption`() {
        val count = productionSources().sumOf { file ->
            Regex("""retryOnConnectionFailure\(false\)""").findAll(file.readText()).count()
        }
        assertEquals(
            "Exactly one exemption is intended -- Career Path, from Phase 16. A second would " +
                "silently remove transport retries from another flow, and an Android 16 " +
                "lifecycle change is precisely the kind of thing someone would reach for it to " +
                "paper over.",
            1, count,
        )
    }

    @Test
    fun `release builds cannot reach a development backend`() {
        assertTrue(
            "the production base URL must be https",
            Regex("""prodApiBaseUrl[\s\S]{0,400}?https://""").containsMatchIn(codeOnly(buildScript())),
        )
        assertTrue(
            "the production flavour must forbid cleartext traffic",
            Regex("""usesCleartextTraffic\s*=\s*"false"""").containsMatchIn(codeOnly(manifest("prod"))),
        )
        val leaks = productionSources().filter { file ->
            val text = file.readText()
            text.contains("http://") || text.contains("localhost") || text.contains("10.0.2.2")
        }
        assertEquals(
            "No production source may name a host or scheme directly. Every base URL comes from " +
                "BuildConfig.API_BASE_URL, which is per-flavour, so a literal here is a dev " +
                "endpoint that would ship inside the release build.",
            emptyList<String>(), leaks.map { it.name },
        )
    }

    @Test
    fun `no screen hand rolls a system bar inset`() {
        // imePadding is deliberately not on this list: the keyboard is the one inset Scaffold does
        // not resolve for you, so a screen may legitimately need it. Everything here, by contrast,
        // is already applied once -- by Scaffold for content and by the Material 3 app bar and
        // navigation bar for themselves -- so a second application is a double inset, which shows
        // up as a gap the height of the status bar rather than as a crash.
        //
        // Phase 28 narrowed this guard, and the reason matters. As written in Phase 27 it asserted
        // that no inset modifier existed anywhere in the app, which was true -- and was the defect:
        // `enableEdgeToEdge()` was on, so the app drew behind the system bars, while the only
        // thing consuming an inset was MainScreen's Scaffold. The other 70 of 98 NavHost
        // destinations inherited nothing and began drawing at y=0, under the clock. Phase 28 adds
        // exactly one inset modifier, in the navigation layer, which every bar-less destination is
        // routed through; see SystemBarInsetContractTest, which pins that contract and which also
        // still forbids any *screen* from applying one of these. So NavGraph.kt is the one
        // permitted site, and this test goes on guarding what it was really protecting: that no
        // individual screen guesses an inset the container has already handled.
        val handRolled = listOf(
            "systemBarsPadding", "navigationBarsPadding", "statusBarsPadding",
            "safeDrawingPadding", "safeContentPadding", "windowInsetsPadding",
            "displayCutoutPadding",
        )
        val contractOwner = "NavGraph.kt"
        val offenders = productionSources().filter { file ->
            val text = file.readText()
            file.name != contractOwner && handRolled.any { text.contains("$it(") }
        }
        assertEquals(
            "The app resolves system-bar insets in exactly two places: the shared contract in " +
                "$contractOwner, for destinations with no bars of their own, and Scaffold content " +
                "padding plus the Material 3 components that inset themselves, for the ones that " +
                "do. A screen applying its own on top of either is a double inset, which shows " +
                "up as a gap the height of the status bar rather than as a crash.",
            emptyList<String>(), offenders.map { it.name },
        )
    }

    @Test
    fun `no component opts its own insets away`() {
        val offenders = productionSources().filter { file ->
            val text = file.readText()
            Regex("""(contentWindowInsets|windowInsets)\s*=""").containsMatchIn(text)
        }
        assertEquals(
            "Passing windowInsets to a Material 3 component is how its inset protection gets " +
                "removed, and WindowInsets(0, 0, 0, 0) on the bottom navigation bar is the exact " +
                "shape of it: the tabs then sit under the gesture area, where the system takes " +
                "the touches and the app looks unresponsive rather than broken.",
            emptyList<String>(), offenders.map { it.name },
        )
    }

    @Test
    fun `no back handler triggers a generation`() {
        // At API 36 every screen is reachable by a back gesture that now runs by default, so a
        // generation on the back path is both more likely to fire and more expensive than before.
        val offenders = productionSources().filter { file ->
            val text = file.readText()
            Regex("""(Predictive)?BackHandler\s*[({][\s\S]{0,400}?generate\(""")
                .containsMatchIn(text)
        }
        assertEquals(
            "Back must never start work. The one legitimate back handler in this app refuses to " +
                "dismiss the accountability alarm and does nothing else; a handler that " +
                "generated something would spend an AI call on a gesture the student meant as " +
                "leaving the screen.",
            emptyList<String>(), offenders.map { it.name },
        )
    }

    @Test
    fun `no screen generates from its composable body`() {
        // A call in the body runs on every recomposition. The server now collapses the duplicates
        // -- Phases 25 and 26 -- so the symptom would not be two roadmaps any more; it would be a
        // screen that silently re-asks forever, which is worse because nothing visibly fails.
        val offenders = productionSources()
            .filter { it.name.endsWith("Screen.kt") }
            .flatMap { file ->
                file.readText().lines()
                    .filter { Regex("""\.generate\w*\(\)""").containsMatchIn(it) }
                    .filter { !it.contains("{") && !it.contains("->") }
                    .map { "${file.name}: ${it.trim()}" }
            }
        assertEquals(
            "A generation must be a callback -- an onClick, or something inside a LaunchedEffect " +
                "-- not a statement in a composable body, where it re-runs on every " +
                "recomposition for as long as the screen is open.",
            emptyList<String>(), offenders,
        )
    }

    @Test
    fun `no credential is hardcoded in production sources`() {
        val pattern = Regex(
            """(?i)\b\w*(secret|password|private_?key|api_?key|apikey|credential)\w*""" +
                """\s*[=:]\s*"[^"]{8,}"""",
        )
        val offenders = productionSources().filter { pattern.containsMatchIn(it.readText()) }
        assertEquals(
            "Every credential this app needs belongs to the backend or to a per-flavour " +
                "BuildConfig field, and nothing in src/main may carry one as a literal: the " +
                "release APK is a zip anyone can unpack, so a secret here is a published secret.",
            emptyList<String>(), offenders.map { it.name },
        )
    }
}
