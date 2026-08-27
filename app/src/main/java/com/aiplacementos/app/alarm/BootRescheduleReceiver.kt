package com.aiplacementos.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aiplacementos.app.database.AlarmDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager registrations don't survive a device reboot -- this re-arms every enabled
 * alarm as soon as the app is able to run again after boot. On some OEM skins this may not
 * fire until the user has unlocked the device at least once post-reboot (this app has no
 * direct-boot-aware component, which would be needed to act earlier -- out of scope here);
 * best-effort, not instant-at-boot, consistent with the rest of this feature's caveats.
 */
class BootRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val scheduler = AlarmScheduler(appContext)
                AlarmDatabase.getInstance(appContext).alarmConfigDao().getAllEnabled().forEach { alarm ->
                    scheduler.scheduleNext(alarm)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
