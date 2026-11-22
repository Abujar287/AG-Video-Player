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

    @Test
    fun `atom xml parser extracts latest release tag, title, and versionCode`() {
        val sampleAtom = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <link rel="alternate" type="text/html" href="https://github.com/Abujar287/AG-Video-Player/releases/tag/v1.0.6"/>
                <title>Video Player v1.0.6</title>
                <content type="html">&lt;p&gt;Automated R8-minified APK build for &lt;strong&gt;Video Player v1.0.6&lt;/strong&gt;.&lt;br&gt;versionCode: 7&lt;/p&gt;</content>
              </entry>
            </feed>
        """.trimIndent()
        val parsed = GitHubUpdateManager.parseLatestFromAtomXml(sampleAtom)
        assertEquals("v1.0.6", parsed?.first)
        assertEquals("Video Player v1.0.6", parsed?.second)
        assertEquals(7, GitHubUpdateManager.parseOptionalVersionCode(parsed?.third.orEmpty()))
    }

    @Test
    fun `apk href extractor finds release apk download link`() {
        val html = """<li><a href="/Abujar287/AG-Video-Player/releases/download/v1.0.6/Video-Player-v1.0.6.apk">Video-Player-v1.0.6.apk</a></li>"""
        assertEquals(
            "/Abujar287/AG-Video-Player/releases/download/v1.0.6/Video-Player-v1.0.6.apk",
            GitHubUpdateManager.extractApkHref(html)
        )
    }
}
