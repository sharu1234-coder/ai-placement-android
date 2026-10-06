package com.aiplacementos.app.platform

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 29: the CI workflow has to keep running the things that catch regressions here.
 *
 * This file exists because of a specific failure, not as a general principle. The instrumentation
 * suite was uncompilable for several phases and nothing noticed, because `androidTest` sources are
 * not built by `assembleDevDebug` or by either unit-test task -- so CI stayed green while
 * `connectedDevDebugAndroidTest` could not be built at all. A repository interface had gained a
 * parameter and the fakes implementing it had not. Phase 27 found it by hand.
 *
 * Phase 29 added `assembleDevDebugAndroidTest` to the workflow. That line is now the only thing
 * standing between this repository and the same silence, and a line in a YAML file is easy to drop
 * during an unrelated edit -- which would restore the original condition exactly, with no test
 * failing to say so. These guards fail instead.
 *
 * They assert on the workflow file rather than on CI behaviour, which is the honest limit of what
 * a unit test can see: they prove the commands are configured, not that a run executed them.
 */
class ContinuousIntegrationGuardTest {

    private fun repoRoot(): File {
        // The unit test's working directory is the app module or the repository root depending on
        // how Gradle was invoked, so the workflow is located by walking up rather than assumed.
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, ".github/workflows").isDirectory) return dir
            dir = dir.parentFile
        }
        throw IllegalStateException(
            "Could not find .github/workflows from ${File(".").absolutePath}; this guard would " +
                "otherwise pass by reading nothing."
        )
    }

    private fun workflow(): String {
        val file = File(repoRoot(), ".github/workflows/ci.yml")
        assertTrue("no CI workflow at ${file.absolutePath}", file.isFile)
        val text = file.readText()
        assertTrue("the CI workflow is suspiciously small (${text.length} chars)", text.length > 500)
        return text
    }

    /** Workflow lines that actually run something, with YAML comments removed. */
    private fun runLines(): List<String> =
        workflow().lines()
            .map { it.substringBefore('#') }
            .filter { it.contains("gradlew") }

    private fun runsTask(task: String): Boolean = runLines().any { it.contains(task) }

    @Test
    fun `ci compiles the instrumentation tests`() {
        assertTrue(
            "CI no longer compiles androidTest. Nothing else in the build graph does: " +
                "assembleDevDebug and the unit-test tasks all leave those sources untouched, so " +
                "the suite can stop compiling and every job stays green -- which is exactly how " +
                "it went unnoticed for several phases before Phase 27 found it by hand.",
            runsTask("assembleDevDebugAndroidTest") || runsTask("connectedDevDebugAndroidTest"),
        )
    }

    @Test
    fun `ci runs both unit test flavors`() {
        val missing = listOf("testDevDebugUnitTest", "testProdDebugUnitTest").filterNot(::runsTask)
        assertEquals(
            "CI must run the unit suite for both flavours: the prod flavour differs in its " +
                "manifest and its base URL, so a dev-only run can pass while the shipped build " +
                "is broken. Missing: $missing",
            emptyList<String>(),
            missing,
        )
    }

    @Test
    fun `ci still builds and lints what it used to`() {
        val missing = listOf(
            "lintDevDebug",
            "assembleDevDebug",
            "assembleProdRelease",
            "bundleProdRelease",
        ).filterNot(::runsTask)
        assertEquals(
            "Phase 29 added a step; it must not have removed one. These were in the workflow " +
                "before and are what prove R8 still shrinks, lint still passes and the " +
                "uploadable bundle still builds: $missing",
            emptyList<String>(),
            missing,
        )
    }

    @Test
    fun `the instrumentation check does not depend on an emulator`() {
        // Compile-only is the deliberate choice: it closes the gap that let the breakage survive
        // without adding a hosted emulator to maintain. If someone later adds a real device run
        // that is an improvement, but it must not be the ONLY check -- an emulator step is
        // flakier and tends to get disabled, taking the compile guarantee with it.
        assertTrue(
            "CI runs instrumentation on a device but no longer compiles it separately. Keep " +
                "assembleDevDebugAndroidTest as well, so the cheap check survives the day the " +
                "emulator step is disabled for flakiness.",
            runsTask("assembleDevDebugAndroidTest"),
        )
    }

    @Test
    fun `the workflow is still structurally a workflow`() {
        val text = workflow()
        assertTrue("the workflow lost its jobs block", text.contains("jobs:"))
        assertTrue("the workflow lost its trigger block", Regex("""^on:""", RegexOption.MULTILINE).containsMatchIn(text))
        assertTrue("the workflow lost its steps", text.contains("steps:"))
        assertTrue(
            "tabs are not valid YAML indentation and will fail the whole workflow to parse",
            !text.lines().any { it.startsWith("\t") },
        )
    }
}
