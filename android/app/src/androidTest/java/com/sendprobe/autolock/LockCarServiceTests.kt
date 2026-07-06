package com.sendprobe.autolock

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sendprobe.autolock.bluelink.BluelinkSession
import com.sendprobe.autolock.bluelink.BluelinkVehicleInfo
import com.sendprobe.autolock.bluelink.LockCarResult
import com.sendprobe.autolock.bluelink.LockCarService
import com.sendprobe.autolock.model.BluelinkCredentials
import com.sendprobe.autolock.storage.BluelinkSessionStore
import com.sendprobe.autolock.storage.CredentialsStore
import com.sendprobe.autolock.storage.LogStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented (not local JVM) because CredentialsStore/BluelinkSessionStore
 * hit the real Android Keystore via SecureStore, which has no local-JVM
 * equivalent.
 */
@RunWith(AndroidJUnit4::class)
class LockCarServiceTests {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        CredentialsStore.clear(context)
        BluelinkSessionStore.clear(context)
        LogStore.get(context).clear()
    }

    @After
    fun tearDown() {
        CredentialsStore.clear(context)
        BluelinkSessionStore.clear(context)
    }

    @Test
    fun dryRunNeverTouchesNetworkAndLogsWithoutCredentials() = runTest {
        val result = LockCarService.lockCar(context, dryRun = true)
        assertEquals(LockCarResult.DryRun, result)

        val entries = LogStore.get(context).all()
        assertEquals(1, entries.size)
        assertEquals("dry_run_lock", entries[0].event)
        assertTrue(entries[0].detail.contains("(none configured)"))
    }

    @Test
    fun dryRunMasksUsernameAndNeverLogsPasswordOrPin() = runTest {
        CredentialsStore.save(context, BluelinkCredentials(username = "driver@example.com", password = "supersecret", pin = "1234"))

        val result = LockCarService.lockCar(context, dryRun = true)
        assertEquals(LockCarResult.DryRun, result)

        val entries = LogStore.get(context).all()
        assertEquals(1, entries.size)
        val detail = entries[0].detail
        assertTrue("expected masked username, got: $detail", detail.contains("dr***@example.com"))
        assertFalse(detail.contains("supersecret"))
        assertFalse(detail.contains("1234"))
    }

    @Test
    fun liveLockSkippedWhenNoCredentialsConfigured() = runTest {
        val result = LockCarService.lockCar(context, dryRun = false)
        assertEquals(LockCarResult.SkippedNoCredentials, result)

        val entries = LogStore.get(context).all()
        assertEquals(1, entries.size)
        assertEquals("lock_skipped", entries[0].event)
    }

    @Test
    fun bluelinkSessionAccessTokenValidity() {
        val stillValid = BluelinkSession(
            accessToken = "tok",
            refreshToken = "refresh",
            tokenExpiryMillis = System.currentTimeMillis() + 300_000
        )
        assertTrue(stillValid.isAccessTokenValid())

        val expired = BluelinkSession(
            accessToken = "tok",
            refreshToken = "refresh",
            tokenExpiryMillis = System.currentTimeMillis() - 5_000
        )
        assertFalse(expired.isAccessTokenValid())

        // Within the ~10s safety margin should count as needing refresh.
        val almostExpired = BluelinkSession(
            accessToken = "tok",
            refreshToken = "refresh",
            tokenExpiryMillis = System.currentTimeMillis() + 5_000
        )
        assertFalse(almostExpired.isAccessTokenValid())
    }

    @Test
    fun bluelinkSessionStoreRoundTrip() {
        val session = BluelinkSession(
            accessToken = "access-123",
            refreshToken = "refresh-456",
            tokenExpiryMillis = System.currentTimeMillis() + 1_800_000,
            vehicle = BluelinkVehicleInfo(regId = "reg-1", vin = "VIN12345", generation = 3, nickname = "Ioniq 5")
        )
        BluelinkSessionStore.save(context, session)
        val loaded = BluelinkSessionStore.load(context)
        assertEquals(session, loaded)

        BluelinkSessionStore.clear(context)
        assertNull(BluelinkSessionStore.load(context))
    }
}
