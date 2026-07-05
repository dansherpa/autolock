package com.sendprobe.autolock.storage

import android.content.Context
import com.sendprobe.autolock.model.LogEntry
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Append-only structured log, persisted to a JSON file in app-private
 * storage so it survives process death and is readable from the in-app
 * log screen without a debugger attached.
 *
 * Deliberately never logs raw credential values -- callers pass in
 * human-readable summaries (e.g. "auth succeeded", "lock command sent")
 * rather than request/response bodies.
 */
class LogStore private constructor(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    fun append(event: String, detail: String = "") {
        synchronized(lock) {
            val entries = readAllLocked().toMutableList()
            entries.add(LogEntry(event = event, detail = detail))
            val trimmed = if (entries.size > MAX_ENTRIES) entries.takeLast(MAX_ENTRIES) else entries
            writeLocked(trimmed)
        }
    }

    fun all(): List<LogEntry> =
        synchronized(lock) { readAllLocked().sortedByDescending { it.timestamp } }

    fun clear() {
        synchronized(lock) { writeLocked(emptyList()) }
    }

    private fun readAllLocked(): List<LogEntry> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<LogEntry>>(file.readText()) }.getOrDefault(emptyList())
    }

    private fun writeLocked(entries: List<LogEntry>) {
        runCatching { file.writeText(json.encodeToString(entries)) }
    }

    companion object {
        private const val MAX_ENTRIES = 500
        private const val FILE_NAME = "autolock-log.json"

        @Volatile private var instance: LogStore? = null

        fun get(context: Context): LogStore =
            instance ?: synchronized(this) {
                instance ?: LogStore(File(context.applicationContext.filesDir, FILE_NAME)).also { instance = it }
            }
    }
}
