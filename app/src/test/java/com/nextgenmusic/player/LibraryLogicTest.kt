package com.nextgenmusic.player

import com.nextgenmusic.player.data.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryLogicTest {
    @Test
    fun repeatModeCyclesInStableOrder() {
        val values = RepeatMode.values()
        assertEquals(RepeatMode.OFF, values[0])
        assertEquals(RepeatMode.ALL, values[1])
        assertEquals(RepeatMode.ONE, values[2])
    }

    @Test
    fun safeFilenameRulesCanBeAppliedWithoutPathTraversal() {
        val raw = "../my song?.mp3"
        val safe = raw.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        assertEquals("my_song_.mp3", safe)
    }
}