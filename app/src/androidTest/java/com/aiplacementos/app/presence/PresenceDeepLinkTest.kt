package com.aiplacementos.app.presence

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The manifest half of the Professional Presence OAuth return.
 *
 * Instrumented rather than a unit test because the thing under test is the merged manifest on a
 * real device: whether `aiplacementos://presence/callback` actually resolves to this app. A unit
 * test cannot see that, and the failure it guards against is silent -- the backend would redirect
 * a student's browser to a scheme nothing handles, stranding them on an error page with the
 * connection already completed server-side and no way back into the app.
 *
 * That was the state of the repository before this phase: the backend had been redirecting to
 * this scheme since the OAuth work landed, and nothing on Android had ever registered it.
 */
@RunWith(AndroidJUnit4::class)
class PresenceDeepLinkTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun resolve(uri: String) =
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW, uri.toUri()),
            PackageManager.MATCH_DEFAULT_ONLY,
        )

    @Test
    fun the_presence_callback_scheme_resolves_to_this_app() {
        val matches = resolve("aiplacementos://presence/callback?provider=github&status=connected")

        assertTrue("no activity handles the presence callback URI", matches.isNotEmpty())
        assertEquals(context.packageName, matches.first().activityInfo.packageName)
    }

    @Test
    fun it_resolves_regardless_of_the_result_carried_in_the_query() {
        // The app reads nothing out of this URI, so every outcome must land the same way. A
        // filter that only matched one of them would strand a student on the others.
        for (status in listOf("connected", "cancelled", "failed")) {
            val matches = resolve("aiplacementos://presence/callback?provider=github&status=$status")
            assertTrue("status=$status did not resolve", matches.isNotEmpty())
        }
    }

    @Test
    fun the_launcher_activity_is_the_one_that_handles_it() {
        // Delivered to the running activity rather than to a second copy of the app -- the
        // reason MainActivity is singleTop. If this ever resolves to a different component,
        // the returning student would land somewhere with no navigation state.
        val matches = resolve("aiplacementos://presence/callback")

        assertTrue(matches.first().activityInfo.name.endsWith("MainActivity"))
    }
}
