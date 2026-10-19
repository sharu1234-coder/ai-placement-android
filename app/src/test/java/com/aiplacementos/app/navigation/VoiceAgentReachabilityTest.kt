package com.aiplacementos.app.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether the realtime Voice Agent, and the one write-action it can offer, are reachable at all.
 *
 * Two different failures are in scope, and neither one is a crash:
 *
 * 1. **A route nobody can enter.** `Screen.kt` builds a route string and `NavGraph.kt` registers
 *    a pattern it must match segment for segment. A mismatch is a silent no-op tap. Every
 *    component of the Voice Agent passed its own unit tests for weeks while no screen
 *    constructed it, which is exactly the failure this half catches.
 *
 * 2. **An action nothing can open.** VA-7's confirmation host is mounted on the agent screen,
 *    but the save-job control renders only when a real listing is in scope. If no caller ever
 *    supplies one, the host is mounted and permanently unopenable -- "implemented, tested,
 *    unreachable" a second time.
 *
 * The second half is read out of the production sources rather than exercised, because what it
 * asserts is a rule about *which code calls what*: a composable that is never rendered cannot be
 * caught by running one screen, and there is no Compose UI test infrastructure in the JVM
 * variant (see `app/build.gradle.kts`: `androidTestImplementation` only). Same technique, and
 * the same honesty about its limits, as `PreparationPlanCallerGuardTest`. It proves the wiring
 * exists in the source; it proves nothing about what a student sees on a handset, which is
 * tracked in `VOICE_DEVICE_ACCEPTANCE_VA11.md`.
 */
class VoiceAgentReachabilityTest {

    private fun fill(pattern: String, vararg args: Pair<String, String>): String {
        var filled = pattern
        args.forEach { (name, value) -> filled = filled.replace("{" + name + "}", value) }
        return filled
    }

    /**
     * Resolved rather than hardcoded because a unit test's working directory is not guaranteed.
     * Failing loudly matters: a guard that quietly scanned nothing would pass forever.
     */
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
    private val agentScreen by lazy { source("ui/voiceagent/VoiceAgentScreen.kt") }
    private val interviewScreen by lazy { source("ui/interview/VoiceInterviewScreen.kt") }
    private val jobDetail by lazy { source("ui/jobhunt/JobDetailScreen.kt") }

    // --------------------------------------------------------------- route ↔ pattern parity

    @Test
    fun `every route a caller builds matches the pattern NavGraph registers`() {
        val registered = { mode: String, interview: String, question: String, listing: String ->
            fill(
                Screen.VoiceAgent.route,
                "mode" to mode,
                "interviewId" to interview,
                "questionId" to question,
                "listingId" to listing,
            )
        }

        // The drawer: coach, no interview, no listing.
        assertEquals(registered("coach", "0", "0", "0"), voiceAgentRoute("coach"))
        // The interview hand-off: both interview ids, no listing.
        assertEquals(
            registered("interview", "7", "31", "0"),
            voiceAgentRoute("interview", interviewId = 7, questionId = 31),
        )
        // The job advert: coach, one listing.
        assertEquals(
            registered("coach", "0", "0", "904"),
            voiceAgentRoute("coach", listingId = 904),
        )
    }

    @Test
    fun `the route declares every argument NavGraph reads`() {
        // Each of these is read with `arguments?.getInt(...)`. A pattern that stopped declaring
        // one would deliver null, and the screen would silently fall back to "absent".
        listOf("{mode}", "{interviewId}", "{questionId}", "{listingId}").forEach {
            assertTrue(it + " is not declared by the route", Screen.VoiceAgent.route.contains(it))
        }
        listOf("interviewId", "questionId", "listingId").forEach {
            assertTrue(
                "NavGraph does not declare " + it + " as an IntType argument",
                navGraph.contains("navArgument(\"" + it + "\") { type = NavType.IntType }"),
            )
        }
    }

    @Test
    fun `the ids survive into the route in their own positions`() {
        // A transposition here would hand the gateway a question id as an interview id, which it
        // refuses -- on a device, and nowhere earlier.
        assertEquals("voice-agent/interview/7/31/0", voiceAgentRoute("interview", 7, 31))
        assertEquals("voice-agent/coach/0/0/904", voiceAgentRoute("coach", listingId = 904))
        assertNotEquals(voiceAgentRoute("interview", 7, 31), voiceAgentRoute("interview", 31, 7))
    }

    @Test
    fun `a voice agent route never collides with a neighbouring one`() {
        val others = listOf(
            Screen.VoiceInterview.route,
            Screen.TextInterview.route,
            Screen.InterviewEvaluation.route,
            Screen.JobHuntDetail.route,
        )
        others.forEach { assertNotEquals(it, Screen.VoiceAgent.route) }
        // Five segments, so a four-segment pattern cannot swallow it either.
        assertEquals(5, Screen.VoiceAgent.route.split("/").size)
    }

    // ------------------------------------------------------- the interview entry point

    @Test
    fun `the interview screen hands off through the closed mode enum`() {
        assertTrue(
            "NavGraph does not wire the interview screen to the agent",
            navGraph.contains("onLiveConversation = { interviewId, questionId ->"),
        )
        assertTrue(
            "the hand-off does not use interview mode",
            navGraph.contains("mode = VoiceAgentMode.INTERVIEW.wire"),
        )
        // A literal would drift from the enum the gateway validates against; the parity test on
        // the backend side pins the enum's spelling to the server's.
        assertFalse(
            "the hand-off hardcodes a mode string instead of using the enum",
            navGraph.contains("voiceAgentRoute(\"interview\""),
        )
    }

    @Test
    fun `the interview screen sends both ids and takes them off the confirmed turn`() {
        assertTrue(
            "the screen does not offer a live conversation",
            interviewScreen.contains("onLiveConversation"),
        )
        // `current` is the `Turn` the server's own next-question read produced. Reading the
        // interview id off anything else is how a stale pairing gets sent.
        assertTrue(
            "the ids do not come from the confirmed turn state",
            interviewScreen.contains("navigate(current.interviewId, current.question.id)"),
        )
        assertTrue(
            "the ViewModel is not told about the hand-off, so the return would not resync",
            interviewScreen.contains("viewModel.onHandedOffToLiveConversation()"),
        )
        assertTrue(
            "the screen never resyncs on return",
            interviewScreen.contains("viewModel.onReturnedToForeground()"),
        )
    }

    // ------------------------------------------------------- the save-job entry point

    @Test
    fun `a real listing screen opens the coach with its own listing`() {
        assertTrue(
            "JobDetailScreen does not offer the coach",
            jobDetail.contains("onDiscussWithCoach"),
        )
        assertTrue(
            "NavGraph does not wire the job screen to the agent with its listing",
            navGraph.contains("voiceAgentRoute(mode = VoiceAgentMode.COACH.wire, listingId = listingId)"),
        )
    }

    @Test
    fun `the save-job action is built with the argument the backend requires`() {
        // `jobs.save` takes `listing_id`, and the backend's ToolInput is `extra="forbid"`: an
        // empty map or a `job_id` would be rejected with a 422 at preview time, which is a
        // button that looks broken rather than one that is honestly absent.
        assertTrue(
            "the save-job control does not send listing_id",
            agentScreen.contains("mapOf(\"listing_id\" to listingId)"),
        )
        assertFalse(
            "the save-job control sends job_id, which the backend does not accept",
            agentScreen.contains("\"job_id\""),
        )
        assertFalse(
            "the save-job control sends an empty argument map",
            agentScreen.contains("ActionCapability.SAVE_JOB, emptyMap()"),
        )
    }

    @Test
    fun `the action is offered only when a listing is actually in scope`() {
        // Not merely disabled: not rendered. A control with nothing to act on cannot be tapped
        // into a 422, and a greyed one reads as something broken.
        assertTrue(
            "the action control is not gated on a real listing",
            agentScreen.contains("if (mode == VoiceAgentMode.COACH && listingId != null)"),
        )
        assertTrue(
            "the confirmation host is not mounted",
            agentScreen.contains("ActionConfirmationHost(viewModel = confirmations)"),
        )
    }

    @Test
    fun `nothing the agent says can open the confirmation`() {
        // The whole VA-7 rule, as a source property: the only call site of `confirmations.request`
        // is the explicit-tap control. A transcript-driven one would be a second call site.
        val requests = Regex("confirmations\\.request").findAll(agentScreen).count()
        assertEquals(
            "there is more than one way to open the confirmation; a spoken yes may be one of them",
            1,
            requests,
        )
        // And it is the button's own lambda that calls it, not a reducer over server events.
        assertTrue(
            agentScreen.contains("onRequest = { capability, arguments -> confirmations.request(capability, arguments) }"),
        )
    }

    // ------------------------------------------------------------------ the drawer route

    @Test
    fun `the drawer route carries no listing, so it offers no action`() {
        // This is the honest consequence of the gate above, pinned so it is not mistaken for a
        // bug later: the general-purpose coach has no advert in scope and therefore no save
        // button. The job screen is the route that has one.
        assertTrue(voiceAgentRoute("coach").endsWith("/0/0/0"))
        val mainScreen = source("navigation/MainScreen.kt")
        assertTrue(
            "the drawer no longer opens the coach",
            mainScreen.contains("voiceAgentRoute(VoiceAgentMode.COACH.wire)"),
        )
    }
}
