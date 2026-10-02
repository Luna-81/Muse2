package com.blue.hush

import android.media.MediaPlayer
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SoundscapePlaybackTest {
    @Test fun bundledRecordingsDecodePauseResumeAndWrapAtTheLoopBoundary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (resource in listOf(R.raw.rain, R.raw.ocean, R.raw.fireplace)) {
            val player = MediaPlayer.create(context, resource)
            assertNotNull("Unable to decode soundscape $resource", player)
            try {
                assertTrue("Recording should last at least two minutes", player.duration >= 120_000)
                player.setVolume(0f, 0f)
                player.isLooping = true
                player.start()
                SystemClock.sleep(250)
                player.pause()
                assertFalse(player.isPlaying)
                val pausedPosition = player.currentPosition
                SystemClock.sleep(150)
                assertEquals(pausedPosition, player.currentPosition)
                val seekComplete = CountDownLatch(1)
                player.setOnSeekCompleteListener { seekComplete.countDown() }
                player.seekTo((player.duration - 600).toLong(), MediaPlayer.SEEK_CLOSEST)
                assertTrue("Seek should complete", seekComplete.await(5, TimeUnit.SECONDS))
                assertTrue(player.currentPosition >= player.duration - 1_000)
                player.start()
                val deadline = SystemClock.elapsedRealtime() + 5_000
                while (player.currentPosition > 5_000 && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(50)
                }
                assertTrue("Loop should remain playing", player.isPlaying)
                assertTrue("Loop should return to the beginning", player.currentPosition < 5_000)
            } finally {
                player.release()
            }
        }
    }
}
