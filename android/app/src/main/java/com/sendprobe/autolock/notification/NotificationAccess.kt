package com.sendprobe.autolock.notification

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Checks and requests the system "Notification Access" permission that
 * lets [BluelinkNotificationListenerService] read notifications posted by
 * the Bluelink app. This is the public, documented
 * Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS flow -- not a workaround.
 */
object NotificationAccess {
    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun openSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
