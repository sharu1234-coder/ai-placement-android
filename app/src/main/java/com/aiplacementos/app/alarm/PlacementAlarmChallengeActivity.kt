package com.aiplacementos.app.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aiplacementos.app.ui.alarm.PlacementAlarmChallengeScreen
import com.aiplacementos.app.ui.theme.AiPlacementOsTheme

/**
 * Standalone cold-start entry point for the ringing alarm -- deliberately NOT part of
 * MainActivity's NavGraph, since it must be reachable via the notification's full-screen
 * intent even if the main app process was fully killed, independent of whatever the normal
 * app's back stack/login state happens to be. showWhenLocked/turnScreenOn (manifest) are the
 * companion pieces to the notification's setFullScreenIntent -- both best-effort, not
 * guaranteed on every OEM/Android version (see PlacementAlarmService's doc comment).
 */
class PlacementAlarmChallengeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val clientAlarmId = intent.getStringExtra(EXTRA_CLIENT_ALARM_ID)
        val alarmConfigId = intent.getLongExtra(EXTRA_ALARM_CONFIG_ID, -1L)
        if (clientAlarmId == null || alarmConfigId == -1L) {
            finish()
            return
        }

        setContent {
            AiPlacementOsTheme {
                PlacementAlarmChallengeScreen(
                    clientAlarmId = clientAlarmId,
                    alarmConfigId = alarmConfigId,
                    onAllSixCorrect = {
                        startService(PlacementAlarmService.stopIntent(this))
                    },
                    onDone = { finishAndRemoveTask() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_CLIENT_ALARM_ID = "client_alarm_id"
        const val EXTRA_ALARM_CONFIG_ID = "alarm_config_id"
    }
}
