package com.aiplacementos.app.ui.common

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F12-11, as a rule rather than a fix: a screen may only tell a student to check their
 * connection when it has established that the connection is what failed.
 *
 * This is deliberately not a ban on the sentence. "Check your connection" is the correct thing
 * to say for a genuine connectivity failure, and it stays -- in [toUserMessage]'s CONNECTIVITY
 * branch, and in the `catch (e: IOException)` branches that have already proved the request
 * never left the device. What it may not be is the answer a `catch (e: Exception)` gives
 * without asking, because that same block also catches a 500, a 429, an expired session and a
 * cancelled coroutine.
 *
 * The check is structural: it looks at the catch clause's type and at whether the block
 * consults the shared classifier or the HTTP status. A typed `catch (e: IOException)` is exempt
 * by construction, which is the distinction the guard exists to preserve.
 */
class ConnectionCopyGuardTest {

    private val classifierCalls = listOf(
        "toUserMessage", "isNetworkError", "isTimeoutError", "toJobSearchFailure",
    )
    private val statusChecks = listOf("HttpException", ".code()")
    private val connectivityWords = listOf(
        "check your connection", "your connection", "no internet", "internet connection",
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

    /** The body of the catch block starting at [openBrace], by brace matching. */
    private fun blockAt(text: String, openBrace: Int): String {
        var depth = 0
        for (i in openBrace until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(openBrace, i + 1)
                }
            }
        }
        return text.substring(openBrace)
    }

    private fun blanketCatchesClaimingConnectivity(file: File): List<String> {
        val text = file.readText()
        val pattern = Regex("""catch\s*\(\s*\w+\s*:\s*(Exception|Throwable)\s*\)\s*\{""")
        return pattern.findAll(text).mapNotNull { match ->
            val body = blockAt(text, match.range.last)
            val lower = body.lowercase()
            val claims = connectivityWords.any { it in lower }
            val asked = classifierCalls.any { it in body } || statusChecks.any { it in body }
            if (claims && !asked) {
                val line = text.substring(0, match.range.first).count { it == '\n' } + 1
                "${file.path}:$line"
            } else {
                null
            }
        }.toList()
    }

    @Test
    fun `no blanket catch tells the student to check a connection it never checked`() {
        val offenders = productionSources().flatMap { blanketCatchesClaimingConnectivity(it) }

        assertTrue(
            "These catch blocks answer every failure -- a 500, a 429, an expired session, a " +
                "cancelled screen -- by blaming the network, without consulting the shared " +
                "classifier or the HTTP status:\n  " + offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the guard scans a realistic number of files`() {
        // Protects the guard itself: a path change that found zero files would make the test
        // above pass while checking nothing.
        assertTrue("expected to scan the production sources", productionSources().size > 100)
    }

    @Test
    fun `the guard would catch the defect it was written for`() {
        val reintroduced = """
            try {
                load()
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't load your profile. Check your connection.")
            }
        """.trimIndent()

        val file = File.createTempFile("guard-check", ".kt").apply { writeText(reintroduced); deleteOnExit() }

        assertEquals(1, blanketCatchesClaimingConnectivity(file).size)
    }

    @Test
    fun `the guard leaves a genuine connectivity-only catch alone`() {
        // The distinction that matters: this one caught IOException, so it knows.
        val legitimate = """
            try {
                load()
            } catch (e: IOException) {
                _uiState.value = UiState.Error("Couldn't reach the server. Check your connection.")
            }
        """.trimIndent()

        val file = File.createTempFile("guard-legit", ".kt").apply { writeText(legitimate); deleteOnExit() }

        assertTrue(blanketCatchesClaimingConnectivity(file).isEmpty())
    }

    @Test
    fun `the guard leaves a blanket catch that asked the classifier alone`() {
        val classified = """
            try {
                load()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("load your profile"))
            }
        """.trimIndent()

        val file = File.createTempFile("guard-classified", ".kt").apply { writeText(classified); deleteOnExit() }

        assertTrue(blanketCatchesClaimingConnectivity(file).isEmpty())
    }
}
