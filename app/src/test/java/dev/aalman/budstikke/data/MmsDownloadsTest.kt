package dev.aalman.budstikke.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsDownloadsTest {

    @Test
    fun aDownloadThatWentQuietCountsAsFailed() {
        val now = 10_000_000L
        val downloading = DownloadState.Downloading.ordinal
        assertEquals(DownloadState.Downloading, MmsDownloads.stateOf(downloading, now - 1_000, now))
        assertEquals(DownloadState.Failed, MmsDownloads.stateOf(downloading, now - MmsDownloads.STALE_MILLIS - 1, now))
        assertEquals(DownloadState.Failed, MmsDownloads.stateOf(DownloadState.Failed.ordinal, now, now))
        assertEquals(DownloadState.Waiting, MmsDownloads.stateOf(99, now, now))
    }

    @Test
    fun mmsKeysNeverMeetSmsIds() {
        val key = MessageKey.mms(42)
        assertTrue(MessageKey.isMms(key))
        assertFalse(MessageKey.isMms(42))
        assertEquals(42L, MessageKey.mmsRowId(key))
    }
}
