package com.sendprobe.autolock.bluelink

import android.content.Context
import com.sendprobe.autolock.storage.AppSettings
import com.sendprobe.autolock.storage.LogStore

/**
 * Single entry point for anything that fires a lock attempt from outside
 * the Settings screen's manual Test Lock button -- currently just the
 * Bluelink notification listener, mirroring iOS's LockCarIntent so both
 * platforms apply the same debounce-then-log-then-lock sequence for the
 * same kind of trigger.
 */
object LockTrigger {
    suspend fun fire(context: Context): LockCarResult {
        val settings = AppSettings.get(context)
        val logStore = LogStore.get(context)

        if (!settings.shouldProceedPastDebounce()) {
            logStore.append(
                "trigger_debounced",
                "ignored -- within ${settings.debounceSeconds.toInt()}s cooldown"
            )
            return LockCarResult.Debounced
        }

        logStore.append("trigger_received")
        return LockCarService.lockCar(context, dryRun = settings.dryRunEnabled)
    }
}
