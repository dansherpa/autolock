package com.sendprobe.autolock.model

import kotlinx.serialization.Serializable

@Serializable
data class BluelinkCredentials(
    val username: String,
    val password: String,
    val pin: String
)
