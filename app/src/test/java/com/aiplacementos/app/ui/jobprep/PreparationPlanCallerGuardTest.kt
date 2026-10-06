package com.aiplacementos.app.ui.jobprep

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 25, as a rule rather than a fix: every way the app asks for a preparation plan must end
 * up at the one endpoint whose server-side guard decides whether to generate.
 *
 * The server is authoritative -- one plan per (student, job description), one generation at a
 * time -- and nothing on the client can or should re-implement that. What the client *can* do is
 * quietly grow a third way to ask. Two already exist and they do not even send the same body:
 * the analyse flow posts the company and title it just collected, and the result screen's 404
 * recovery posts neither. That is exactly why the business guard cannot be keyed on the request:
 * two callers, two different requests, one plan.
 *
 * So this guard pins the callers rather than their behaviour. A new one is not forbidden; it has
 * to be added here deliberately, which is the point at which someone has to ask whether it reads
 * before it writes and whether it carries a key.
 *
 * Structural for the same reason [ConnectionCopyGuardTest] is: a rule about which code may call
 * what cannot be checked by running one screen.
 */
class PreparationPlanCallerGuardTest {

    /** The callers that exist, each with why it is allowed to generate. */
    private val knownCallers = mapOf(
        "JobMatchPrepViewModel.kt" to
            "the analyse flow, generating once for a job description it has just parsed",
        "JobMatchPrepResultViewModel.kt" to
            "the result screen, which GETs first and posts only on a 404",
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

    private fun callers(): Map<String, String> =
        productionSources()
            .filter { file ->
                file.name !in setOf("JobRepository.kt", "JobRepositoryImpl.kt", "JobsApi.kt") &&
                    file.readText().contains("generatePreparationPlan")
            }
            .associate { it.name to it.readText() }

    @Test
    fun `the preparation plan is requested from exactly the callers this phase audited`() {
        assertEquals(
            "a new caller of generatePreparationPlan appeared. Add it to knownCallers once you " +
                "have checked that it reads before it writes and sends an Idempotency-Key -- the " +
                "server collapses duplicates per (profile, job description), but a caller that " +
                "skips the GET still spends a request to find that out.",
            knownCallers.keys,
            callers().keys,
        )
    }

    @Test
    fun `every caller sends an idempotency key`() {
        callers().forEach { (name, source) ->
            val call = source.substringAfter("generatePreparationPlan(")
            assertTrue(
                "$name calls generatePreparationPlan without an Idempotency-Key, so a lost " +
                    "response would be retransmitted as a brand-new operation",
                call.contains("idempotencyKey"),
            )
        }
    }

    @Test
    fun `no caller reaches the endpoint without going through the repository`() {
        // The repository method is the single seam the key is threaded through. A ViewModel
        // holding JobsApi directly would send a request that no test and no guard here can see.
        callers().forEach { (name, source) ->
            assertTrue(
                "$name appears to use JobsApi directly; go through JobRepository so the key and " +
                    "the endpoint stay in one place",
                !source.contains("JobsApi"),
            )
        }
    }

    @Test
    fun `the result screen still reads before it writes`() {
        // The cheap path, and the one that keeps the invariant free in the common case: a job
        // description that already has its plan is answered by a GET, with no claim and no model
        // call. Losing this would not break the invariant -- the server would still collapse the
        // request -- but every reopen would pay for a round trip that finds out nothing new.
        val source = callers().getValue("JobMatchPrepResultViewModel.kt")
        val beforeGenerate = source.substringBefore("generatePreparationPlan(")

        assertTrue(
            "JobMatchPrepResultViewModel must call getPreparationPlan before it generates one",
            beforeGenerate.contains("getPreparationPlan("),
        )
        assertTrue(
            "the generate call must still be the 404 recovery path, not an unconditional post",
            beforeGenerate.contains("404"),
        )
    }
}
