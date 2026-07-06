package com.sendprobe.autolock

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sendprobe.autolock.bluelink.LockCarResult
import com.sendprobe.autolock.bluelink.LockTrigger
import com.sendprobe.autolock.storage.AppSettings
import com.sendprobe.autolock.storage.BluelinkSessionStore
import com.sendprobe.autolock.storage.CredentialsStore
import com.sendprobe.autolock.storage.LogStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockTriggerTests {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var settings: AppSettings

    @Before
    fun setUp() {
        CredentialsStore.clear(context)
        BluelinkSessionStore.clear(context)
        LogStore.get(context).clear()
        settings = AppSettings.get(context)
        settings.updateDryRunEnabled(true)
        settings.updateDebounceSeconds(90f)
        settings.lastTriggerMillis = 0L
    }

    @After
    fun tearDown() {
        CredentialsStore.clear(context)
        BluelinkSessionStore.clear(context)
        settings.lastTriggerMillis = 0L
    }

    @Test
    fun firstTriggerProceedsAndLogsReceivedThenDryRun() = runTest {
        LockTrigger.fire(context)

        val events = LogStore.get(context).all().map { it.event }.reversed()
        assertEquals(listOf("trigger_received", "dry_run_lock"), events)
    }

    @Test
    fun secondTriggerWithinCooldownIsDebounced() = runTest {
        LockTrigger.fire(context)
        LogStore.get(context).clear()

        val result = LockTrigger.fire(context)

        val events = LogStore.get(context).all()
        assertEquals(1, events.size)
        assertEquals("trigger_debounced", events[0].event)
        assertEquals(LockCarResult.Debounced, result)
    }

    @Test
    fun triggerAfterCooldownWindowProceedsAgain() = runTest {
        settings.updateDebounceSeconds(1f)
        LockTrigger.fire(context)
        LogStore.get(context).clear()
        // Simulate cooldown expiry deterministically rather than sleeping in the test.
        settings.lastTriggerMillis = System.currentTimeMillis() - 2_000L

        LockTrigger.fire(context)

        val events = LogStore.get(context).all().map { it.event }
        assertTrue(events.contains("trigger_received"))
    }
}
