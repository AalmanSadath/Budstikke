package dev.aalman.budstikke.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitWithinTest {

    @Test
    fun aPictureThatFitsIsLeftAlone() {
        assertNull(fitWithin(800, 600, 1024))
        assertNull(fitWithin(1024, 1024, 1024))
    }

    @Test
    fun theLongerSideComesDownToTheLimitKeepingTheShape() {
        assertEquals(1024 to 768, fitWithin(4000, 3000, 1024))
        assertEquals(576 to 1024, fitWithin(2250, 4000, 1024))
    }

    @Test
    fun aHugeClaimedSizeStillGivesASmallTarget() {
        // A forged picture can claim any size; the decoder is asked for this one instead.
        assertEquals(1024 to 1, fitWithin(65_000, 10, 1024))
    }
}
