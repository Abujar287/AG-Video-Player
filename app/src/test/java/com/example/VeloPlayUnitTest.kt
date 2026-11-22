package com.example

import com.example.updater.GitHubUpdateManager
import com.example.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VeloPlayUnitTest {
    @Test
    fun formatDuration_handlesSecondsAndHours() {
        assertEquals("00:00", FormatUtils.formatDuration(0L))
        assertEquals("01:05", FormatUtils.formatDuration(65_000L))
        assertEquals("1:01:05", FormatUtils.formatDuration(3_665_000L))
    }

    @Test
    fun versionComparison_detectsNewerVersion() {
        assertTrue(GitHubUpdateManager.isNewerVersion("1.0.0", 1, "1.0.10", 11))
        assertFalse(GitHubUpdateManager.isNewerVersion("1.0.10", 11, "1.0.9", 9))
    }
}
