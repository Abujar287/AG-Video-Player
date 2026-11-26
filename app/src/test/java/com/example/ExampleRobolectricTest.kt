package com.example

import com.example.data.model.UserPreferences
import com.example.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExampleRobolectricTest {
    @Test
    fun appResourcesAndFormatting_areConfigured() {
        val stringsFile = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml")
        ).firstOrNull { it.exists() }
        if (stringsFile != null) {
            val content = stringsFile.readText()
            assertTrue(content.contains("<string name=\"app_name\">Video Player</string>"))
        }

        val prefs = UserPreferences()
        assertEquals(0, prefs.gestureSeekStepSeconds)
        assertEquals("1080p · 1920×1080", FormatUtils.formatResolution(1920, 1080))
    }
}
