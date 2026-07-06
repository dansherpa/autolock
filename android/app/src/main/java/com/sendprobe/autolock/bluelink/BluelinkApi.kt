package com.sendprobe.autolock.bluelink

import com.sendprobe.autolock.model.BluelinkCredentials
import java.io.IOException
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Ported from the Hyundai US Bluelink flow used by bluelinky (Node.js) and
 * hyundai_kia_connect_api (Python) -- there is no official public API, so
 * these endpoints, headers, and payload shapes are reverse-engineered.
 * hyundai_kia_connect_api's HyundaiBlueLinkApiUSA.py is treated as the
 * primary source of truth here since it documents a live-confirmed fix
 * (refresh requires username+password alongside the refresh token, not the
 * refresh token alone) more recent than bluelinky's implementation.
 */
object BluelinkApi {
    private const val HOST = "api.telematics.hyundaiusa.com"
    private const val LOGIN_BASE = "https://$HOST/v2/ac/"
    private const val API_BASE = "https://$HOST/ac/v2/"

    private const val CLIENT_ID = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920"
    private const val CLIENT_SECRET = "v558o935-6nne-423i-baa8"

    private val jsonMediaType = "application/json;charset=UTF-8".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val utcOffsetHours: String
        get() = (TimeZone.getDefault().rawOffset / 3_600_000).toString()

    /**
     * Shared headers sent on every request. Mirrors the header set
     * HyundaiBlueLinkApiUSA.py sends (User-Agent, brandIndicator, etc.) --
     * deviating from this reverse-engineered shape risks the request being
     * rejected as coming from an unrecognized client.
     */
    private fun baseHeaders(): Headers = Headers.Builder()
        .add("content-type", "application/json;charset=UTF-8")
        .add("accept", "application/json, text/plain, */*")
        .add("accept-language", "en-US,en;q=0.9")
        .add(
            "user-agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.142 Safari/537.36"
        )
        .add("origin", "https://$HOST")
        .add("referer", "https://$HOST/login")
        .add("from", "SPA")
        .add("to", "ISS")
        .add("language", "0")
        .add("offset", utcOffsetHours)
        .add("sec-fetch-dest", "empty")
        .add("sec-fetch-mode", "cors")
        .add("sec-fetch-site", "same-origin")
        .add("refresh", "false")
        .add("encryptFlag", "false")
        .add("brandIndicator", "H")
        .add("client_id", CLIENT_ID)
        .add("clientSecret", CLIENT_SECRET)
        .build()

    private fun authenticatedHeaders(credentials: BluelinkCredentials, accessToken: String): Headers =
        baseHeaders().newBuilder()
            .add("username", credentials.username)
            .add("accessToken", accessToken)
            .add("blueLinkServicePin", credentials.pin)
            .build()

    private fun vehicleHeaders(
        credentials: BluelinkCredentials,
        accessToken: String,
        vehicle: BluelinkVehicleInfo
    ): Headers =
        authenticatedHeaders(credentials, accessToken).newBuilder()
            .add("registrationId", vehicle.regId)
            .add("gen", vehicle.generation.toString())
            .add("vin", vehicle.vin)
            .build()

    private inline fun <reified T> jsonBody(value: T) =
        json.encodeToString(value).toRequestBody(jsonMediaType)

    // MARK: - Auth

    suspend fun login(username: String, password: String): BluelinkSession {
        val request = Request.Builder()
            .url(LOGIN_BASE + "oauth/token")
            .headers(baseHeaders())
            .post(jsonBody(LoginRequestBody(username, password)))
            .build()
        return sessionFrom(send(request))
    }

    suspend fun refresh(username: String, password: String, refreshToken: String): BluelinkSession {
        val request = Request.Builder()
            .url(LOGIN_BASE + "oauth/token")
            .headers(baseHeaders())
            .post(jsonBody(RefreshRequestBody(username = username, password = password, refreshToken = refreshToken)))
            .build()
        return sessionFrom(send(request))
    }

    private fun sessionFrom(data: String): BluelinkSession {
        val decoded = json.decodeFromString<BluelinkTokenResponse>(data)
        if (decoded.errorCode != null) {
            throw BluelinkException.AuthenticationFailed("${decoded.errorCode}: ${decoded.errorMessage ?: "unknown error"}")
        }
        val accessToken = decoded.accessToken
        if (accessToken.isNullOrEmpty()) {
            throw BluelinkException.AuthenticationFailed("no access_token in response")
        }
        return BluelinkSession(
            accessToken = accessToken,
            refreshToken = decoded.refreshToken.orEmpty(),
            tokenExpiryMillis = System.currentTimeMillis() + (decoded.expiresInSecondsOrZero * 1000).toLong(),
            vehicle = null
        )
    }

    // MARK: - Vehicles

    suspend fun getVehicles(credentials: BluelinkCredentials, accessToken: String): List<BluelinkVehicleInfo> {
        val request = Request.Builder()
            .url(API_BASE + "enrollment/details/${credentials.username}")
            .headers(authenticatedHeaders(credentials, accessToken))
            .get()
            .build()
        val decoded = json.decodeFromString<EnrolledVehiclesResponse>(send(request))
        if (decoded.errorCode != null) {
            throw BluelinkException.AuthenticationFailed("${decoded.errorCode}: ${decoded.errorMessage ?: "unknown error"}")
        }
        val entries = decoded.enrolledVehicleDetails ?: throw BluelinkException.NoVehicleFound
        return entries
            .filter { it.vehicleDetails.enrollmentStatus != "CANCELLED" }
            .map {
                BluelinkVehicleInfo(
                    regId = it.vehicleDetails.regid,
                    vin = it.vehicleDetails.vin,
                    generation = it.vehicleDetails.vehicleGeneration?.toIntOrNull() ?: 2,
                    nickname = it.vehicleDetails.nickName
                )
            }
    }

    // MARK: - Commands

    /**
     * Sends the lock/unlock command and returns the transaction ID (from the
     * tmsTid/transactionId/Xid response header) if one was present, so the
     * caller can poll checkActionStatus for confirmation. Control commands
     * can return HTTP 200 with an empty body on success, so an empty body is
     * not treated as an error.
     */
    suspend fun sendLockAction(
        action: BluelinkLockAction,
        credentials: BluelinkCredentials,
        accessToken: String,
        vehicle: BluelinkVehicleInfo
    ): String? {
        val headers = vehicleHeaders(credentials, accessToken, vehicle).newBuilder()
            .add("APPCLOUD-VIN", vehicle.vin)
            .build()
        val request = Request.Builder()
            .url(API_BASE + action.endpointPath)
            .headers(headers)
            .post(jsonBody(LockActionRequestBody(credentials.username, vehicle.vin)))
            .build()

        val (data, response) = sendWithResponse(request)

        if (data.isNotEmpty()) {
            val decoded = runCatching { json.decodeFromString<BluelinkActionStatusResponse>(data) }.getOrNull()
            if (decoded?.errorCode != null) {
                throw BluelinkException.CommandFailed("${decoded.errorCode}: ${decoded.errorMessage ?: "unknown error"}")
            }
        }

        for (key in listOf("tmsTid", "transactionId", "Xid")) {
            response.header(key)?.let { return it }
        }
        return null
    }

    suspend fun checkActionStatus(
        transactionId: String,
        credentials: BluelinkCredentials,
        accessToken: String,
        vehicle: BluelinkVehicleInfo
    ): BluelinkOrderStatus {
        val headers = vehicleHeaders(credentials, accessToken, vehicle).newBuilder()
            .add("tid", transactionId)
            .add("login_id", credentials.username)
            .add("service_type", "REMOTE_POLL")
            .build()
        val request = Request.Builder()
            .url(API_BASE + "rmt/getRunningStatus")
            .headers(headers)
            .get()
            .build()

        val data = send(request)
        if (data.isEmpty()) return BluelinkOrderStatus.UNKNOWN

        val decoded = json.decodeFromString<BluelinkActionStatusResponse>(data)
        return when (decoded.status) {
            "SUCCESS" -> BluelinkOrderStatus.SUCCESS
            "ERROR" -> BluelinkOrderStatus.FAILED
            "PENDING" -> BluelinkOrderStatus.PENDING
            else -> BluelinkOrderStatus.UNKNOWN
        }
    }

    // MARK: - Transport

    private suspend fun send(request: Request): String = sendWithResponse(request).first

    private suspend fun sendWithResponse(request: Request): Pair<String, Response> = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw BluelinkException.NetworkError(e)
        }
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw BluelinkException.UnexpectedResponse("HTTP ${it.code}")
            }
            body to it
        }
    }
}
