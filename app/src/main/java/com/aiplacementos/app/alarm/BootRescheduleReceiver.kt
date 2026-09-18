package com.aiplacementos.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aiplacementos.app.database.AlarmDatabase
import com.aiplacementos.app.network.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager registrations don't survive a device reboot -- this re-arms every enabled
 * alarm as soon as the app is able to run again after boot. On some OEM skins this may not
 * fire until the user has unlocked the device at least once post-reboot (this app has no
 * direct-boot-aware component, which would be needed to act earlier -- out of scope here);
 * best-effort, not instant-at-boot, consistent with the rest of this feature's caveats.
 *
 * Re-arming is conditional on somebody being signed in. This receiver is the one path that can
 * bring alarms back without anyone opening the app, so it is also the one path that could undo
 * a logout: logout cancels the pending intents, and a reboot would otherwise read the rows
 * straight back out and re-arm them. Logout now empties the table as well, which alone would
 * leave nothing to re-arm -- but relying on that would make this receiver's correctness depend
 * on another component having done its job, and this is the component that runs when no user
 * and no UI is present. Checking for a session here is the local guarantee.
 */
class BootRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescheduleEnabledAlarms(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /**
         * Re-arm every enabled alarm, unless nobody is signed in. Returns how many were armed.
         *
         * Separated from [onReceive] so the session guard can be tested for what it actually
         * does. Calling `onReceive` directly from a test does not work: `goAsync()` returns
         * null outside a real system dispatch, and the resulting NPE lands on a background
         * thread during whichever test happens to be running -- which is exactly how it
         * presented the first time, as a failure in an unrelated Compose test.
         */
        internal suspend fun rescheduleEnabledAlarms(appContext: Context): Int {
            // No session, nothing to re-arm. An alarm firing for an account that is no longer
            // signed in is at best confusing and at worst shows the previous student's notes
            // to whoever is holding the phone.
            if (TokenStore(appContext).currentAccessToken() == null) return 0

            val scheduler = AlarmScheduler(appContext)
            val alarms = AlarmDatabase.getInstance(appContext).alarmConfigDao().getAllEnabled()
            alarms.forEach { alarm -> scheduler.scheduleNext(alarm) }
            return alarms.size
        }
    }
}
