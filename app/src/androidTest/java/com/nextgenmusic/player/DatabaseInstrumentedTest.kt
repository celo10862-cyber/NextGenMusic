package com.nextgenmusic.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nextgenmusic.player.data.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database = AppDatabase(context)

    @Test
    fun settingsRoundTrip() {
        database.putSetting("test_key", "test_value")
        assertEquals("test_value", database.getSetting("test_key", "missing"))
    }

    @After
    fun close() {
        database.close()
        context.deleteDatabase("next_gen_music.db")
    }
}