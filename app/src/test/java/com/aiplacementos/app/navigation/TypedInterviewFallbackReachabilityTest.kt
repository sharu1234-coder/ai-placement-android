package com.aiplacementos.app.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H2/H12: whether a student on a server that cannot transcribe can finish an interview at all.
 *
 * Before this change the Full Recruiter Simulation was the one interview in the app with no
 * typed path: SimulationSetupScreen said in its own KDoc that the interview "runs on the
 * existing TextInterviewScreen", and NavGraph navigated every student to
 * voiceInterviewRoute(jdId, "full_simulation") unconditionally. Screen.kt carried the same
 * false claim. Three places described a route that did not exist, and every component on the
 * typed path passed its own tests throughout -- the defect was only ever in the wiring.
 *
 * So this guard is about reachability, not existence. The first half builds the route the graph
 * actually navigates to and checks it against the pattern the graph registers, segment for
 * segment, which is the check a passing unit test on either side cannot make. The second half
 * is read out of the production sources, because what it asserts is a rule about which code
 * calls what -- same technique, and the same honesty about its limits, as
 * VoiceAgentReachabilityTest: it proves the wiring exists in the source and proves nothing
 * about what a student sees on a handset.
 */
class TypedInterviewFallbackReachabilityTest {

    private fun fill(pattern: String, vararg args: Pair<String, String>): String {
        var filled = pattern
        args.forEach { (name, value) -> filled = filled.replace("{" + name + "}", value) }
        return filled
    }

    /** Resolved rather than hardcoded because a unit test working directory is not guaranteed.
     *  Failing loudly matters: a guard that quietly scanned nothing would pass forever. */
    private fun source(relative: String): String {
        val base = "src/main/java/com/aiplacementos/app/"
        val candidates = listOf(File(base + relative), File("app/" + base + relative), File("../app/" + base + relative))
        val found = candidates.firstOrNull { it.isFile }
        requireNotNull(found) {
            "Could not locate " + relative + " from " + File(".").absolutePath +
                "; this guard must not silently scan nothing."
        }
        return found.readText()
    }

    private val navGraph by lazy { source("navigation/NavGraph.kt") }
    private val setupScreen by lazy { source("ui/interview/SimulationSetupScreen.kt") }
    private val categoryScreen by lazy { source("ui/interview/MockInterviewCategoryScreen.kt") }

    // ------------------------------------------------------------- route to pattern parity

    @Test
    fun `the typed simulation route matches the pattern NavGraph registers`() {
        val registered = fill(Screen.TextInterview.route, "jdId" to "7", "type" to FULL_SIMULATION_TYPE)

        assertEquals(
            "A student on a server that cannot transcribe is sent here, so a mismatch is a " +
                "simulation that cannot be started at all.",
            registered,
            fullSimulationRoute(jdId = 7, spoken = false),
        )
    }

    @Test
    fun `the spoken simulation route still matches its own registered pattern`() {
        val registered = fill(Screen.VoiceInterview.route, "jdId" to "7", "type" to FULL_SIMULATION_TYPE)

        assertEquals(registered, fullSimulationRoute(jdId = 7, spoken = true))
    }

    @Test
    fun `the two simulation routes are different destinations`() {
        // If these ever collapse to one, the capability flag stops meaning anything and the
        // fallback silently becomes the thing it was meant to replace.
        assertTrue(fullSimulationRoute(1, spoken = true) != fullSimulationRoute(1, spoken = false))
    }

    // ------------------------------------------------------------------ live path wiring

    @Test
    fun `NavGraph routes the simulation through the capability-aware builder`() {
        assertTrue(
            "NavGraph must send the simulation through fullSimulationRoute so the typed " +
                "destination is reachable; a hardcoded voiceInterviewRoute here is the " +
                "original defect.",
            navGraph.contains("fullSimulationRoute("),
        )
    }

    @Test
    fun `the simulation setup screen asks the server before choosing a route`() {
        // The screen that launches the interview is the one that must know. A hardcoded true
        // here would make the typed route unreachable again while this file still passed.
        assertTrue(
            "SimulationSetupScreen must consult SpeechAvailabilityViewModel.",
            setupScreen.contains("SpeechAvailabilityViewModel"),
        )
        assertTrue(
            "SimulationSetupScreen must pass the server answer, not a literal, to onStart.",
            setupScreen.contains("speech.canInterviewByVoice"),
        )
    }

    @Test
    fun `the category screen asks the server before offering spoken answers`() {
        assertTrue(categoryScreen.contains("SpeechAvailabilityViewModel"))
        assertTrue(
            "The per-category choice must follow the server answer, not the saved preference " +
                "alone -- otherwise a student who once chose Speaking is sent to a voice " +
                "interview the server cannot serve.",
            categoryScreen.contains("effectiveMode"),
        )
    }

    @Test
    fun `the voice interview screen has a typed way out wired to it`() {
        assertTrue(
            "VoiceInterviewScreen must be given onSwitchToTyping by NavGraph, or its " +
                "capability-refusal state offers an explanation with no way forward.",
            navGraph.contains("onSwitchToTyping ="),
        )
    }
}
