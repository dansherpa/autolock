package com.sendprobe.autolock.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val event: String,
    val detail: String = ""
)
