package com.sendprobe.autolock.bluelink

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Persisted Bluelink session: tokens plus the vehicle identifiers needed to
 * address commands at a specific car. Cached across app relaunches (a
 * notification trigger fires in a fresh process) so a normal lock trigger
 * costs a token refresh + one command call rather than a full login +
 * vehicle lookup every time -- both to be fast and to conserve Bluelink's
 * daily rate limit.
 */
@Serializable
data class BluelinkSession(
    val accessToken: String,
    val refreshToken: String,
    val tokenExpiryMillis: Long,
    val vehicle: BluelinkVehicleInfo? = null
) {
    /** Matches bluelinky's ~10s safety margin before treating a token as needing refresh. */
    fun isAccessTokenValid(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis + 10_000L < tokenExpiryMillis
}

@Serializable
data class BluelinkVehicleInfo(
    val regId: String,
    val vin: String,
    val generation: Int,
    val nickname: String
)

enum class BluelinkLockAction(val endpointPath: String) {
    LOCK("rcs/rdo/off"),
    UNLOCK("rcs/rdo/on")
}

enum class BluelinkOrderStatus {
    PENDING, SUCCESS, FAILED, UNKNOWN
}

// MARK: - Wire request shapes

@Serializable
internal data class LoginRequestBody(
    val username: String,
    val password: String
)

@Serializable
internal data class RefreshRequestBody(
    val username: String,
    val password: String,
    @SerialName("grant_type") val grantType: String = "refresh_token",
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
internal data class LockActionRequestBody(
    @SerialName("userName") val userName: String,
    val vin: String
)

// MARK: - Wire response shapes

@Serializable
internal data class BluelinkTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    // Observed as both a JSON number and a numeric string across API
    // responses; kept as a raw element and coerced by expiresInSecondsOrZero.
    @SerialName("expires_in") val expiresIn: JsonElement? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null
) {
    val expiresInSecondsOrZero: Double
        get() = (expiresIn as? JsonPrimitive)?.doubleOrNull ?: 0.0
}

@Serializable
internal data class EnrolledVehiclesResponse(
    val enrolledVehicleDetails: List<Entry>? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null
) {
    @Serializable
    internal data class Entry(val vehicleDetails: Details)

    @Serializable
    internal data class Details(
        val regid: String,
        val nickName: String,
        val vin: String,
        val vehicleGeneration: String? = null,
        val enrollmentStatus: String? = null
    )
}

@Serializable
internal data class BluelinkActionStatusResponse(
    val status: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null
)
