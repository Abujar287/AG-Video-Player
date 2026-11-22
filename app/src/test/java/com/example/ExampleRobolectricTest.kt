package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleRobolectricTest {
    @Test
    fun appContextAndStrings_areConfigured() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertNotNull(app)
        assertEquals("Video Player", app.getString(R.string.app_name))
        assertEquals("1080p · 1920×1080", FormatUtils.formatResolution(1920, 1080))
    }
}
