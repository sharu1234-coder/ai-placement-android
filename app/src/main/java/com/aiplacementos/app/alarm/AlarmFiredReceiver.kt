package com.aiplacementos.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aiplacementos.app.database.AlarmDatabase
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when a scheduled Placement Alarm goes off. Starting a foreground service from here is
 * a documented Android background-start exemption (the app receiving an AlarmManager-
 * triggered broadcast) -- no workaround needed. Kept fast: DB work happens in a goAsync()-
 * backed coroutine so the receiver doesn't block the main thread past its execution window.
 */
class AlarmFiredReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmConfigId = intent.getLongExtra(EXTRA_ALARM_CONFIG_ID, -1L)
        if (alarmConfigId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAlarmFired(context.applicationContext, alarmConfigId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAlarmFired(context: Context, alarmConfigId: Long) {
        val database = AlarmDatabase.getInstance(context)
        val alarmConfig = database.alarmConfigDao().getById(alarmConfigId) ?: return

        // Reuse the same clientAlarmId if this alarm instance already has an unfinished
        // challenge (e.g. the receiver was somehow invoked twice before the student
        // finished) -- otherwise this firing is genuinely new.
        val existing = database.challengeDao().getActiveChallengeForAlarm(alarmConfigId)
        val clientAlarmId = existing?.clientAlarmId ?: UUID.randomUUID().toString()

        ContextCompat.startForegroundService(
            context,
            Intent(context, PlacementAlarmService::class.java).apply {
                putExtra(PlacementAlarmService.EXTRA_CLIENT_ALARM_ID, clientAlarmId)
                putExtra(PlacementAlarmService.EXTRA_ALARM_CONFIG_ID, alarmConfigId)
            },
        )

        // Exact alarms are one-shot -- a repeating alarm re-arms its own next occurrence
        // right when it fires, rather than being registered once as recurring. A one-shot
        // alarm's AlarmManager registration is consumed by firing, so it must be disabled here
        // too -- otherwise it stays enabled=true forever (AlarmListScreen's Switch keeps
        // showing it as "on" though it will never ring again) and BootRescheduleReceiver would
        // silently resurrect and reschedule an already-completed one-shot alarm after a reboot.
        if (alarmConfig.repeatDays.isNotEmpty()) {
            AlarmScheduler(context).scheduleNext(alarmConfig)
        } else {
            database.alarmConfigDao().update(alarmConfig.copy(enabled = false))
        }
    }

    companion object {
        const val EXTRA_ALARM_CONFIG_ID = "alarm_config_id"
    }
}
