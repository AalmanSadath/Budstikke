package dev.aalman.budstikke.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongTextTest {

    private val carrierSetsFour = MmsLimits.DEFAULT.copy(textThreshold = 4)
    private val carrierSetsNone = MmsLimits.DEFAULT

    @Test
    fun followingTheCarrierUsesItsThreshold() {
        assertFalse(carrierSetsFour.sendsAsMms(parts = 4, setting = null))
        assertTrue(carrierSetsFour.sendsAsMms(parts = 5, setting = null))
    }

    @Test
    fun aCarrierWithoutAThresholdLeavesItOff() {
        assertFalse(carrierSetsNone.sendsAsMms(parts = 10, setting = null))
    }

    @Test
    fun turnedOnWithoutACarrierThresholdAllowsThreeParts() {
        assertFalse(carrierSetsNone.sendsAsMms(parts = 3, setting = true))
        assertTrue(carrierSetsNone.sendsAsMms(parts = 4, setting = true))
    }

    @Test
    fun turnedOffAlwaysSendsSms() {
        assertFalse(carrierSetsFour.sendsAsMms(parts = 20, setting = false))
    }
}
