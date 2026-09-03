package com.aiplacementos.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aiplacementos.app.R

/**
 * Owns the alarm's ringing state from the moment it fires until the student solves all 6
 * questions. The only code path that silences the alarm is stopRingingAndSelf(), triggered
 * exclusively by the challenge screen reaching 6/6 correct (see stopIntent()) -- a plain
 * notification swipe does not stop a foreground service or its MediaPlayer, so Back/home/
 * swipe genuinely cannot silence it.
 *
 * Sound loops on AudioAttributes.USAGE_ALARM, which routes through the device's Alarm volume
 * stream and bypasses silent/vibrate mode the same way a real alarm clock app does -- the
 * sanctioned mechanism for this, not a workaround. foregroundServiceType="mediaPlayback"
 * (declared in the manifest) is the correct type: the service's actual technical work here
 * is audio playback, matching that type's own definition.
 */
class PlacementAlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRingingAndSelf()
            return START_NOT_STICKY
        }

        val clientAlarmId = intent?.getStringExtra(EXTRA_CLIENT_ALARM_ID)
        val alarmConfigId = intent?.getLongExtra(EXTRA_ALARM_CONFIG_ID, -1L) ?: -1L
        if (clientAlarmId == null || alarmConfigId == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(clientAlarmId, alarmConfigId))
        startRinging()
        return START_STICKY
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    private fun startRinging() {
        if (mediaPlayer != null) return // already ringing -- e.g. a duplicate service start

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            isLooping = true
            setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer error while ringing (what=$what, extra=$extra)")
                true
            }
            try {
                setDataSource(this@PlacementAlarmService, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                setOnPreparedListener { it.start() }
                prepareAsync()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start alarm sound: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun stopRingingAndSelf() {
        releasePlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayer() {
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
            } catch (_: IllegalStateException) {
                // Not yet prepared/already stopped -- nothing to do.
            }
            release()
        }
        mediaPlayer = null
    }

    private fun buildNotification(clientAlarmId: String, alarmConfigId: Long): Notification {
        ensureChannel()

        // Request code must be per-alarm (matching AlarmScheduler's own convention), and
        // FLAG_UPDATE_CURRENT must replace any cached PendingIntent's extras -- Android's
        // PendingIntent cache key ignores Intent extras, so a hardcoded request code of 0
        // would let a stale PendingIntent from a previous alarm's firing (still cached under
        // the same key) get reused here with the *old* alarm's clientAlarmId/alarmConfigId,
        // opening the wrong alarm's challenge screen on tap.
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmConfigId.toInt(),
            Intent(this, PlacementAlarmChallengeActivity::class.java).apply {
                putExtra(PlacementAlarmChallengeActivity.EXTRA_CLIENT_ALARM_ID, clientAlarmId)
                putExtra(PlacementAlarmChallengeActivity.EXTRA_ALARM_CONFIG_ID, alarmConfigId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("Placement Alarm")
            .setContentText("Solve 6 questions to stop the alarm")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)

        // USE_FULL_SCREEN_INTENT may not be auto-granted for this app -- its core function
        // isn't alarms/calling, the category Android auto-grants it to (see AlarmScheduler's
        // doc comment). Degrade gracefully to a high-priority heads-up notification whose
        // tap still opens the challenge if the permission isn't available -- best-effort
        // lock-screen wake, not a guarantee, per the product spec's own instruction.
        val canUseFullScreenIntent = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            NotificationManagerCompat.from(this).canUseFullScreenIntent()
        if (canUseFullScreenIntent) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        return builder.build()
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Placement Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts for your scheduled Placement Accountability Alarm"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_CLIENT_ALARM_ID = "client_alarm_id"
        const val EXTRA_ALARM_CONFIG_ID = "alarm_config_id"
        const val ACTION_STOP = "com.aiplacementos.app.alarm.ACTION_STOP"
        private const val CHANNEL_ID = "placement_alarm"
        private const val NOTIFICATION_ID = 9001
        private const val TAG = "PlacementAlarmService"

        fun stopIntent(context: Context): Intent =
            Intent(context, PlacementAlarmService::class.java).setAction(ACTION_STOP)
    }
}
