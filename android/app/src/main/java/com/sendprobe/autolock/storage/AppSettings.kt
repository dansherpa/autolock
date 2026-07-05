package com.sendprobe.autolock.storage

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide settings backed by SharedPreferences, exposed as Compose state
 * so the Settings screen recomposes on change. Centralized here (rather
 * than scattered per-screen state) so the notification listener added in
 * a later step reads the same values the Settings screen writes.
 */
class AppSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var dryRunEnabled: Boolean by mutableStateOf(prefs.getBoolean(KEY_DRY_RUN, true))
        private set

    var debounceSeconds: Float by mutableFloatStateOf(prefs.getFloat(KEY_DEBOUNCE, 90f))
        private set

    fun updateDryRunEnabled(value: Boolean) {
        dryRunEnabled = value
        prefs.edit().putBoolean(KEY_DRY_RUN, value).apply()
    }

    fun updateDebounceSeconds(value: Float) {
        debounceSeconds = value
        prefs.edit().putFloat(KEY_DEBOUNCE, value).apply()
    }

    var lastTriggerMillis: Long
        get() = prefs.getLong(KEY_LAST_TRIGGER, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_TRIGGER, value).apply() }

    /**
     * Returns true if enough time has passed since the last trigger to
     * allow another lock attempt, and records [now] as the new
     * last-trigger time as a side effect when it does.
     */
    fun shouldProceedPastDebounce(now: Long = System.currentTimeMillis()): Boolean {
        val last = lastTriggerMillis
        if (last != 0L && now - last < debounceSeconds * 1000L) {
            return false
        }
        lastTriggerMillis = now
        return true
    }

    companion object {
        private const val PREFS_NAME = "com.sendprobe.autolock.settings"
        private const val KEY_DRY_RUN = "dryRunEnabled"
        private const val KEY_DEBOUNCE = "debounceSeconds"
        private const val KEY_LAST_TRIGGER = "lastTriggerMillis"

        @Volatile private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
    }
}
