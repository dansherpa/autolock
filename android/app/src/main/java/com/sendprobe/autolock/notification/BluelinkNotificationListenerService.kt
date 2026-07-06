package com.sendprobe.autolock.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sendprobe.autolock.bluelink.LockTrigger
import com.sendprobe.autolock.storage.LogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Listens for the vehicle-unlocked notification Bluelink itself posts and
 * fires the debounce-checked lock flow when it matches. The source-app
 * filter lives in BluelinkNotificationSource and the text match in
 * BluelinkUnlockMatcher, both isolated so either can be tuned without
 * touching this class.
 *
 * Every notification from a likely-Bluelink source is logged (title +
 * text) regardless of match, so real-world wording can be captured from
 * the in-app log viewer and used to populate BluelinkUnlockMatcher.
 */
class BluelinkNotificationListenerService : NotificationListenerService() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (!BluelinkNotificationSource.isLikelySource(packageName)) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        val logStore = LogStore.get(applicationContext)
        logStore.append("notification_seen", "pkg=$packageName title=\"$title\" text=\"$text\"")

        if (!BluelinkUnlockMatcher.isUnlockAlert(title, text)) return

        logStore.append("unlock_alert_matched", "pkg=$packageName")
        serviceScope.launch {
            LockTrigger.fire(applicationContext)
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
