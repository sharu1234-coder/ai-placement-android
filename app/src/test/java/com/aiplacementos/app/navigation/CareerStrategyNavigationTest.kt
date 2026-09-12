package com.aiplacementos.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D1's own regression test: the job-specific strategy must stay reachable.
 *
 * The original defect was not a broken function. `analyzeJob(listingId)` worked, had five
 * passing tests, and was called by nothing -- so the feature was fully tested and completely
 * invisible. Tests that assert a method exists would all have passed while the student could
 * never get to it.
 *
 * What actually makes the feature reachable is a contract between two files: `Screen.kt` builds
 * the route a button navigates to, and `NavGraph.kt` registers a pattern it must match. Rename
 * one without the other and the button silently goes nowhere -- the same class of failure,
 * reintroduced. These tests pin that contract.
 *
 * This is a JVM test, so it proves the wiring rather than the pixels; rendering is covered by
 * the ViewModel and section tests. Instrumented UI coverage needs a device, which is recorded
 * as not verified rather than implied here.
 */
class CareerStrategyNavigationTest {

    /** What NavGraph does with the registered pattern, mirrored: substitute the argument. */
    private fun fill(pattern: String, vararg args: Pair<String, String>): String {
        var filled = pattern
        args.forEach { (name, value) -> filled = filled.replace("{$name}", value) }
        return filled
    }

    @Test
    fun `the job strategy route a caller builds matches the pattern NavGraph registers`() {
        val built = careerStrategyJobRoute(42)
        val registered = fill(Screen.CareerStrategyForJob.route, "listingId" to "42")

        assertEquals(registered, built)
    }

    @Test
    fun `the listing id survives into the route`() {
        assertTrue(careerStrategyJobRoute(512).endsWith("/512"))
        assertNotEquals(careerStrategyJobRoute(1), careerStrategyJobRoute(2))
    }

    @Test
    fun `the job route is distinct from the plain strategy route`() {
        """Two different questions need two different back-stack entries. If these collapsed,
        pressing back from a job would leave the student on a screen that had silently changed
        meaning."""
        assertNotEquals(Screen.CareerStrategy.route, Screen.CareerStrategyForJob.route)
        assertNotEquals(Screen.CareerStrategy.route, careerStrategyJobRoute(7))
    }

    @Test
    fun `the job strategy route declares the argument NavGraph reads`() {
        """NavGraph reads "listingId" as an IntType argument. If the pattern stopped declaring
        it, the argument would arrive null and the screen would analyse listing 0."""
        assertTrue(Screen.CareerStrategyForJob.route.contains("{listingId}"))
    }

    @Test
    fun `the job detail route still matches what the strategy entry point expects`() {
        """The other direction: Job Detail is where the student opens job strategy from, so its
        own route has to keep working too."""
        val built = jobHuntDetailRoute(99)
        val registered = fill(Screen.JobHuntDetail.route, "listingId" to "99")

        assertEquals(registered, built)
    }
}
