package com.sendprobe.autolock.bluelink

sealed class BluelinkException(message: String) : Exception(message) {
    class AuthenticationFailed(detail: String) : BluelinkException("Authentication failed: $detail")
    object NoVehicleFound : BluelinkException("No enrolled vehicle found on this Bluelink account")
    class CommandFailed(detail: String) : BluelinkException("Command failed: $detail")
    class UnexpectedResponse(detail: String) : BluelinkException("Unexpected response: $detail")
    class NetworkError(cause: Throwable) : BluelinkException("Network error: ${cause.message}")
}
