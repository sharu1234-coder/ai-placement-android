package com.aiplacementos.app.push

import android.content.Context

/** Whether AppFirebaseMessagingService should show a system notification banner for an
 * incoming push. Backed by plain SharedPreferences -- this app has no other local
 * key-value preference store, and a single boolean doesn't warrant introducing DataStore.
 * Muting only suppresses the banner/sound; the push is still recorded to the in-app
 * Notification Center either way (see onMessageReceived) so nothing is silently lost. */
object NotificationPreferences {
    private const val PREFS_NAME = "notification_preferences"
    private const val KEY_ENABLED = "notifications_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
