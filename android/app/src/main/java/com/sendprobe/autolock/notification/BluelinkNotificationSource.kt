package com.sendprobe.autolock.notification

/**
 * Which installed app's notifications count as coming from Bluelink.
 * Deliberately a broad substring match rather than one hardcoded package --
 * Hyundai ships region-specific variants under different developer
 * accounts (the confirmed US app is com.stationdm.bluelink; a separate
 * com.hyundai.bluelink.ap exists for another market), and this runs
 * against whichever one is actually installed on the phone.
 */
object BluelinkNotificationSource {
    private val PACKAGE_NAME_HINTS = listOf("hyundai", "bluelink")

    fun isLikelySource(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return PACKAGE_NAME_HINTS.any { lower.contains(it) }
    }
}
