package com.sendprobe.autolock.bluelink

import android.content.Context
import com.sendprobe.autolock.model.BluelinkCredentials
import com.sendprobe.autolock.storage.BluelinkSessionStore
import com.sendprobe.autolock.storage.CredentialsStore
import com.sendprobe.autolock.storage.LogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

sealed class LockCarResult {
    object DryRun : LockCarResult()
    object Success : LockCarResult()
    object ConfirmedFailure : LockCarResult()
    object SentUnconfirmed : LockCarResult()
    object SkippedNoCredentials : LockCarResult()
    data class Failed(val message: String) : LockCarResult()
}

/**
 * Orchestrates a single lock attempt: dry-run short-circuit, credential
 * loading, session reuse/refresh/login, sending the command, and a short
 * best-effort confirmation poll. This is the single entry point both the
 * in-app "Test Lock" button and the (future) notification-triggered lock
 * call, so the two paths can never drift.
 */
object LockCarService {
    suspend fun lockCar(context: Context, dryRun: Boolean): LockCarResult = withContext(Dispatchers.IO) {
        val logStore = LogStore.get(context)

        if (dryRun) {
            val username = CredentialsStore.load(context)?.username
            logStore.append("dry_run_lock", "would have called lock() for account ${maskedUsername(username)}")
            return@withContext LockCarResult.DryRun
        }

        val credentials = CredentialsStore.load(context)
        if (credentials == null) {
            logStore.append("lock_skipped", "no credentials configured")
            return@withContext LockCarResult.SkippedNoCredentials
        }

        try {
            var session = validSession(context, credentials)

            var vehicle = session.vehicle
            if (vehicle == null) {
                vehicle = BluelinkApi.getVehicles(credentials, session.accessToken).firstOrNull()
                    ?: throw BluelinkException.NoVehicleFound
                session = session.copy(vehicle = vehicle)
                BluelinkSessionStore.save(context, session)
            }

            val transactionId = BluelinkApi.sendLockAction(
                BluelinkLockAction.LOCK,
                credentials,
                session.accessToken,
                vehicle
            )

            logStore.append(
                "lock_command_sent",
                transactionId?.let { "transactionId=$it" } ?: "no transaction id returned"
            )

            if (transactionId == null) {
                return@withContext LockCarResult.SentUnconfirmed
            }

            when (val status = pollForConfirmation(transactionId, credentials, session.accessToken, vehicle)) {
                BluelinkOrderStatus.SUCCESS -> {
                    logStore.append("lock_confirmed_success")
                    LockCarResult.Success
                }
                BluelinkOrderStatus.FAILED -> {
                    logStore.append("lock_confirmed_failed")
                    LockCarResult.ConfirmedFailure
                }
                else -> {
                    logStore.append("lock_unconfirmed", "status still $status after polling window")
                    LockCarResult.SentUnconfirmed
                }
            }
        } catch (e: Exception) {
            val message = e.message ?: e.toString()
            logStore.append("lock_error", message)
            LockCarResult.Failed(message)
        }
    }

    /**
     * Returns a session with a not-yet-expired access token, refreshing or
     * logging in as needed, and persists whatever session results.
     */
    private suspend fun validSession(context: Context, credentials: BluelinkCredentials): BluelinkSession {
        val logStore = LogStore.get(context)
        val cached = BluelinkSessionStore.load(context)
        if (cached != null && cached.isAccessTokenValid()) {
            return cached
        }

        if (cached != null) {
            try {
                val refreshed = BluelinkApi.refresh(credentials.username, credentials.password, cached.refreshToken)
                    .copy(vehicle = cached.vehicle)
                BluelinkSessionStore.save(context, refreshed)
                logStore.append("token_refreshed")
                return refreshed
            } catch (e: Exception) {
                logStore.append("token_refresh_failed", "falling back to full login")
            }
        }

        val fresh = BluelinkApi.login(credentials.username, credentials.password)
        BluelinkSessionStore.save(context, fresh)
        logStore.append("login_succeeded")
        return fresh
    }

    /**
     * Polls checkActionStatus a handful of times with a short delay. Lock
     * commands are near-instant physically, but the API's own confirmation
     * can lag a couple seconds behind the HTTP 200 that accepted the
     * command, so treat "still pending after the window" as inconclusive
     * rather than a failure.
     */
    private suspend fun pollForConfirmation(
        transactionId: String,
        credentials: BluelinkCredentials,
        accessToken: String,
        vehicle: BluelinkVehicleInfo
    ): BluelinkOrderStatus {
        val maxAttempts = 6
        repeat(maxAttempts) { attempt ->
            val status = try {
                BluelinkApi.checkActionStatus(transactionId, credentials, accessToken, vehicle)
            } catch (e: Exception) {
                null
            }
            if (status == BluelinkOrderStatus.SUCCESS || status == BluelinkOrderStatus.FAILED) {
                return status
            }
            if (attempt < maxAttempts - 1) {
                delay(2000)
            }
        }
        return BluelinkOrderStatus.UNKNOWN
    }

    private fun maskedUsername(username: String?): String {
        if (username.isNullOrEmpty()) return "(none configured)"
        val atIndex = username.indexOf('@')
        if (atIndex < 0) return username.take(2) + "***"
        val local = username.substring(0, atIndex)
        return local.take(2) + "***" + username.substring(atIndex)
    }
}
