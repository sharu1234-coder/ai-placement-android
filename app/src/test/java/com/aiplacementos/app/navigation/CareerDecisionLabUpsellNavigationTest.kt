package com.aiplacementos.app.navigation

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F12-08's call-to-action. The Career Decision Lab paywall's only action is "Upgrade to Premium",
 * wired in NavGraph to Screen.Subscriptions. The copy was stale; this destination was not, and
 * these assertions are here so that stays true while the copy around it changes.
 *
 * The loop case is the one worth guarding: an upsell whose button returns to the very screen the
 * student cannot open yet is a dead end, and it looks like a working button right up until it is
 * tapped. The tap itself is verified on a device -- a NavGraph lambda is not visible from a unit
 * test, so this covers the constants it resolves and the device covers the wiring.
 */
class CareerDecisionLabUpsellNavigationTest {

    @Test
    fun `the upgrade destination is a real route, not the gated screen it was reached from`() {
        assertTrue(Screen.Subscriptions.route.isNotBlank())
        assertNotEquals(
            "the upsell CTA would send the student back to the screen they cannot open",
            Screen.CareerDecisionLab.route,
            Screen.Subscriptions.route,
        )
    }

    @Test
    fun `the gated screen keeps its own distinct route`() {
        // Career Decision Lab sits among several career-* routes; a collision would open the
        // wrong screen from the drawer and make the paywall look broken rather than the route.
        val neighbours = listOf(
            Screen.CareerTwin.route,
            Screen.CareerStrategy.route,
            Screen.CareerPathHome.route,
            Screen.Subscriptions.route,
        )
        assertTrue(neighbours.none { it == Screen.CareerDecisionLab.route })
    }
}
