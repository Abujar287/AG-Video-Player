package com.example

import com.example.updater.GitHubUpdateManager
import com.example.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VeloPlayUnitTest {

    @Test
    fun `semantic version comparison detects newer GitHub releases`() {
        assertTrue(
            GitHubUpdateManager.isNewerVersion(
                currentVersionName = "1.0.0",
                currentVersionCode = 1,
                remoteVersionName = "1.0.1",
                remoteVersionCode = null
            )
        )
        assertTrue(
            GitHubUpdateManager.isNewerVersion(
                currentVersionName = "1.0.0",
                currentVersionCode = 1,
                remoteVersionName = "1.0.0",
                remoteVersionCode = 2
            )
        )
        assertFalse(
            GitHubUpdateManager.isNewerVersion(
                currentVersionName = "1.2.0",
                currentVersionCode = 3,
                remoteVersionName = "1.1.9",
                remoteVersionCode = 3
            )
        )
    }

    @Test
    fun `versionCode parser extracts metadata from GitHub release notes`() {
        val code = GitHubUpdateManager.parseOptionalVersionCode("Fixes PiP aspect ratio\nversionCode: 42")
        assertEquals(42, code)
    }

    @Test
    fun `format utils formats duration and file sizes cleanly`() {
        assertEquals("02:05", FormatUtils.formatDuration(125_000L))
        assertEquals("1:01:05", FormatUtils.formatDuration(3_665_000L))
        assertEquals("1.5 MB", FormatUtils.formatFileSize(1_572_864L))
        assertEquals("1080p · 1920×1080", FormatUtils.formatResolution(1920, 1080))
    }
}
