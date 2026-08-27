package com.aiplacementos.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.aiplacementos.app.database.entity.AlarmConfigEntity
import java.util.Calendar

/**
 * Wraps AlarmManager for the Placement Accountability Alarm. Uses setAlarmClock (not
 * setExactAndAllowWhileIdle) deliberately -- it's the one AlarmManager API built for
 * user-visible "alarm clock" semantics: full Doze/App-Standby exemption (a stronger
 * guarantee than the other exact-alarm APIs, which some OEM battery managers can still
 * defer) and a status-bar alarm-clock icon confirming to the student that a real alarm is
 * set. This is best-effort, not an absolute guarantee -- OEM-specific battery management
 * beyond stock Android's Doze can still interfere in ways no public API overrides.
 *
 * Exact alarms are inherently one-shot: a repeating alarm is re-armed for its next
 * occurrence by AlarmFiredReceiver every time it fires, not registered once as recurring.
 *
 * Requires SCHEDULE_EXACT_ALARM (user-grantable via Settings, revokable at any time) rather
 * than USE_EXACT_ALARM -- the latter is reserved by Play policy for apps whose core
 * functionality *is* alarms/calendaring, which this app's core functionality is not (it's a
 * placement-prep app with an alarm feature). canScheduleExactAlarms() must be checked before
 * every schedule call since the user can revoke the permission at any time after granting it.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Settings screen the user must visit to grant SCHEDULE_EXACT_ALARM -- there is no
     * runtime permission dialog for it, only this system settings deep link. */
    fun requestExactAlarmPermissionIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))

    fun scheduleNext(alarm: AlarmConfigEntity) {
        if (!alarm.enabled || !canScheduleExactAlarms()) return

        val triggerAtMillis = nextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDays)
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            Intent(context, PlacementAlarmChallengeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val firePendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            Intent(context, AlarmFiredReceiver::class.java).apply {
                putExtra(AlarmFiredReceiver.EXTRA_ALARM_CONFIG_ID, alarm.id)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
            firePendingIntent,
        )
    }

    fun cancel(alarmConfigId: Long) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmConfigId.toInt(),
            Intent(context, AlarmFiredReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    companion object {
        /** Computes the next wall-clock trigger time. repeatDays is a comma-separated set of
         * Calendar.SUNDAY(1)..Calendar.SATURDAY(7); empty means one-shot -- next occurrence
         * of hour:minute, tomorrow if that time has already passed today. */
        fun nextTriggerMillis(hour: Int, minute: Int, repeatDays: String, now: Calendar = Calendar.getInstance()): Long {
            val days = repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

            val candidate = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (days.isEmpty()) {
                if (candidate.timeInMillis <= now.timeInMillis) {
                    candidate.add(Calendar.DAY_OF_YEAR, 1)
                }
                return candidate.timeInMillis
            }

            for (offset in 0..7) {
                val attempt = (candidate.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
                val isEligibleDay = attempt.get(Calendar.DAY_OF_WEEK) in days
                val isInFuture = attempt.timeInMillis > now.timeInMillis
                if (isEligibleDay && isInFuture) {
                    return attempt.timeInMillis
                }
            }
            // Unreachable in practice (every weekday recurs within 7 days), but keeps the
            // function total rather than throwing if repeatDays somehow parsed to nothing.
            return candidate.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        }
    }
}
