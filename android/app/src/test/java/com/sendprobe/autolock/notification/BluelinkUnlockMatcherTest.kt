package com.sendprobe.autolock.notification

import org.junit.Assert.assertFalse
import org.junit.Test

class BluelinkUnlockMatcherTest {
    @Test
    fun neverMatchesUntilRealPhrasesAreCaptured() {
        assertFalse(BluelinkUnlockMatcher.isUnlockAlert("Vehicle Unlocked", "Your car was unlocked"))
        assertFalse(BluelinkUnlockMatcher.isUnlockAlert("", ""))
    }
}
