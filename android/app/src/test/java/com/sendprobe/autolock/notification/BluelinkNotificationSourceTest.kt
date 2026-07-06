package com.sendprobe.autolock.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluelinkNotificationSourceTest {
    @Test
    fun matchesConfirmedUsBluelinkPackage() {
        assertTrue(BluelinkNotificationSource.isLikelySource("com.stationdm.bluelink"))
    }

    @Test
    fun matchesOtherMarketHyundaiPackage() {
        assertTrue(BluelinkNotificationSource.isLikelySource("com.hyundai.bluelink.ap"))
    }

    @Test
    fun rejectsUnrelatedPackage() {
        assertFalse(BluelinkNotificationSource.isLikelySource("com.google.android.gm"))
    }
}
