package com.sendprobe.autolock.notification

/**
 * Isolated so the unlock-alert wording can be updated in one place once
 * real notification text has been captured from the in-app log viewer --
 * Hyundai's exact wording is not a stable API contract and can change
 * between Bluelink app updates.
 *
 * Starts empty on purpose: with no phrases configured this never matches,
 * so BluelinkNotificationListenerService only observes and logs
 * notifications from a likely Bluelink source until real text has been
 * captured and added here.
 */
object BluelinkUnlockMatcher {
    private val UNLOCK_ALERT_PHRASES: List<String> = emptyList()

    fun isUnlockAlert(title: String, text: String): Boolean {
        if (UNLOCK_ALERT_PHRASES.isEmpty()) return false
        val haystack = "$title $text".lowercase()
        return UNLOCK_ALERT_PHRASES.any { haystack.contains(it.lowercase()) }
    }
}
