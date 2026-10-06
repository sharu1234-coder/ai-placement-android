package com.aiplacementos.app.ui.placementpath

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 26, as a rule rather than a fix: every way the app asks for a placement path must end up
 * at the one endpoint whose server-side guard decides whether to generate.
 *
 * The server is authoritative -- one active path per student, one generation at a time, and a
 * later deliberate request may still regenerate -- and nothing on the client can or should
 * re-implement that. What the client *can* do is quietly grow a third way to ask. Two exist, they
 * carry different keys, and together they are the race this phase closes: onboarding generates
 * the first path, and if that request is lost in transport the Placement Path screen finds no
 * current path and its Generate button sends another one.
 *
 * So this guard pins the callers rather than their behaviour. A new one is not forbidden; it has
 * to be added here deliberately, which is the point at which someone has to ask whether its key
 * is stable across retries and fresh for a new deliberate request.
 *
 * Structural for the same reason [ConnectionCopyGuardTest] is: a rule about which code may call
 * what cannot be checked by running one screen.
 */
class PlacementPathCallerGuardTest {

    /** The callers that exist, each with why it is allowed to generate. */
    private val knownCallers = mapOf(
        "OnboardingConfirmationViewModel.kt" to
            "the end of onboarding, generating the student's first path",
        "PlacementPathViewModel.kt" to
            "the Placement Path screen's explicit regenerate",
    )

    /**
     * Resolved rather than hardcoded because a unit test's working directory is not guaranteed.
     * Failing loudly matters here: a guard that quietly scanned nothing would pass forever.
     */
    private fun productionSources(): List<File> {
        val relative = "src/main/java/com/aiplacementos/app"
        val candidates = listOf(File(relative), File("app/$relative"), File("../app/$relative"))
        val root = candidates.firstOrNull { it.isDirectory }
        requireNotNull(root) {
            "Could not locate production sources from ${File(".").absolutePath}; this guard " +
                "would otherwise pass by scanning nothing."
        }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** Files that call generate() on the placement-path repository, excluding the seam itself. */
    private fun callers(): Map<String, String> =
        productionSources()
            .filter { file ->
                file.name !in setOf(
                    "PlacementPathRepository.kt", "PlacementPathRepositoryImpl.kt",
                    "PlacementPathApi.kt",
                ) && Regex("placementPathRepository\\s*\\.\\s*generate\\(")
                    .containsMatchIn(file.readText())
            }
            .associate { it.name to it.readText() }

    @Test
    fun `the placement path is generated from exactly the callers this phase audited`() {
        assertEquals(
            "a new caller of placementPathRepository.generate appeared. Add it to knownCallers " +
                "once you have checked that it reuses one key across retries and takes a fresh " +
                "one for a new deliberate request -- the server collapses requests that overlap " +
                "a generation, but it cannot tell a retry from a new intent on the client's " +
                "behalf.",
            knownCallers.keys,
            callers().keys,
        )
    }

    @Test
    fun `every caller sends an idempotency key`() {
        callers().forEach { (name, source) ->
            val call = source.substringAfter("placementPathRepository.generate(")
            val argument = call.substringBefore(")")
            assertTrue(
                "$name calls generate() without a key, so a lost response would be " +
                    "retransmitted as a brand-new 163-second generation",
                argument.isNotBlank(),
            )
        }
    }

    @Test
    fun `every caller holds its key across a retry and releases it only on success`() {
        // The Phase 24-D rule, which both callers implement differently and both get right:
        // onboarding uses OperationKeyHolder, and the Placement Path screen holds a single
        // nullable String because the request carries no parameters at all, so only one identity
        // can ever exist. What matters is that neither mints a fresh key per attempt.
        callers().forEach { (name, source) ->
            val holdsKey = source.contains("OperationKeyHolder") ||
                source.contains("pendingGenerateKey")
            assertTrue(
                "$name must hold its key across retries rather than minting one per attempt",
                holdsKey,
            )
            assertTrue(
                "$name must release its key only after the generation succeeded",
                source.contains("consume()") || source.contains("pendingGenerateKey = null"),
            )
        }
    }

    @Test
    fun `no caller reaches the endpoint without going through the repository`() {
        // The repository method is the single seam the key is threaded through. A ViewModel
        // holding PlacementPathApi directly would send a request no guard here can see.
        callers().forEach { (name, source) ->
            assertTrue(
                "$name appears to use PlacementPathApi directly; go through " +
                    "PlacementPathRepository so the key and the endpoint stay in one place",
                !source.contains("PlacementPathApi"),
            )
        }
    }

    @Test
    fun `the placement path screen still reads before it offers to generate`() {
        // Not a correctness requirement -- the server collapses an overlapping duplicate either
        // way -- but it is what keeps the common case free: a student who already has a path sees
        // it from a GET, and the Generate button is an explicit choice rather than something
        // every screen open pays 163 seconds for.
        val source = callers().getValue("PlacementPathViewModel.kt")

        assertTrue(
            "PlacementPathViewModel must load the current path before generating one",
            source.indexOf("getCurrent()") < source.indexOf("placementPathRepository.generate("),
        )
    }
}
