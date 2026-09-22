package com.aiplacementos.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Career Goals has to be reachable, and its preparation steps have to land somewhere real.
 *
 * Same reasoning as CareerStrategyNavigationTest: a feature can be fully implemented, fully
 * tested and completely invisible, because what makes it reachable is a contract between two
 * files -- `Screen.kt` builds a route and `NavGraph.kt` registers a pattern it must match.
 *
 * The second half matters more here than usual. Career Goals owns no interview or practice
 * flow of its own; every step of its plan hands off through the destination vocabulary, and a
 * destination the table does not know falls through to a screen that has nothing to do with
 * the step the student just tapped. That is a silent failure, not a crash.
 */
class CareerGoalNavigationTest {

    private fun fill(pattern: String, vararg args: Pair<String, String>): String {
        var filled = pattern
        args.forEach { (name, value) -> filled = filled.replace("{$name}", value) }
        return filled
    }

    @Test
    fun `the detail route a caller builds matches the pattern NavGraph registers`() {
        val built = careerGoalDetailRoute(42)
        val registered = fill(Screen.CareerGoalDetail.route, "careerGoalId" to "42")

        assertEquals(registered, built)
    }

    @Test
    fun `the goal id survives into the route`() {
        assertTrue(careerGoalDetailRoute(512).endsWith("/512"))
        assertNotEquals(careerGoalDetailRoute(1), careerGoalDetailRoute(2))
    }

    @Test
    fun `the detail route declares the argument NavGraph reads`() {
        // NavGraph reads "careerGoalId" as an IntType. If the pattern stopped declaring it, the
        // argument would arrive null and the screen would load goal 0.
        assertTrue(Screen.CareerGoalDetail.route.contains("{careerGoalId}"))
    }

    @Test
    fun `the list and detail routes are distinct`() {
        assertNotEquals(Screen.CareerGoals.route, Screen.CareerGoalDetail.route)
        assertNotEquals(Screen.CareerGoals.route, careerGoalDetailRoute(7))
    }

    @Test
    fun `a career goal route never collides with an existing one`() {
        // "career-goals" and "career-goal-detail" must not be swallowed by, or swallow, the
        // neighbouring career-* routes this app already registers.
        val existing = listOf(
            Screen.CareerStrategy.route,
            Screen.CareerStrategyForJob.route,
            Screen.CareerTwin.route,
            Screen.CareerPathHome.route,
            Screen.CareerDecisionLab.route,
        )
        assertTrue(existing.none { it == Screen.CareerGoals.route })
        assertTrue(existing.none { it == Screen.CareerGoalDetail.route })
    }

    @Test
    fun `every destination the preparation plan can emit resolves to a real screen`() {
        """The backend's plan only ever uses these keys (see career_goal_service's
        _preparation_plan). Each must map to a distinct real route rather than falling through
        to the table's else branch, which would quietly send the student to Career Strategy
        instead of the practice the step described."""
        val planDestinations = listOf(
            "project_builder",
            "practice",
            "recruiter_attack",
            "full_simulation",
            "skill_passport",
            "interview",
            "resume_ats",
        )

        for (destination in planDestinations) {
            val route = careerStrategyDestinationRoute(destination)
            assertNotEquals(
                "\"$destination\" fell through to the default route",
                Screen.CareerStrategy.route,
                route,
            )
            assertTrue("\"$destination\" produced an empty route", route.isNotBlank())
        }
    }

    @Test
    fun `the two destinations Career Goals added map to the screens they name`() {
        assertEquals(Screen.RecruiterAttackList.route, careerStrategyDestinationRoute("recruiter_attack"))
        assertEquals(Screen.SimulationSetup.route, careerStrategyDestinationRoute("full_simulation"))
    }

    @Test
    fun `an unknown destination from a newer backend still lands somewhere safe`() {
        // A no-op-ish fallback is a small disappointment; a crash is not.
        assertEquals(
            Screen.CareerStrategy.route,
            careerStrategyDestinationRoute("a_destination_added_later"),
        )
    }
}
