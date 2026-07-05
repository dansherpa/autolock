package com.sendprobe.autolock.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Declared now (rather than in a later step) so this app actually appears
 * as a togglable entry in system Notification Access settings -- without
 * at least one declared listener service, there is nothing for that
 * screen's toggle to control.
 *
 * Package filtering and unlock-text matching land in a later step; for
 * now this deliberately does nothing.
 */
class BluelinkNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Intentionally empty until the Bluelink package is confirmed
        // and match logic is implemented.
    }
}
