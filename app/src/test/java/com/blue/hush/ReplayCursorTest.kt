package com.blue.hush

import com.blue.hush.replay.ReplayCursor
import com.blue.hush.session.StateSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReplayCursorTest {
    @Test
    fun returnsSamplesAtClampedProgress() {
        val cursor = ReplayCursor(listOf(StateSample(0), StateSample(1), StateSample(2)))
        assertEquals(0, cursor.sampleAt(-1f)?.elapsedSeconds)
        assertEquals(1, cursor.sampleAt(0.5f)?.elapsedSeconds)
        assertEquals(2, cursor.sampleAt(2f)?.elapsedSeconds)
    }

    @Test
    fun preservesGapDuringReplay() {
        val sample = ReplayCursor(listOf(StateSample(0), StateSample(1))).sampleAt(1f)
        assertFalse(sample?.valid == true)
    }

    @Test fun seeksByElapsedTimeWithoutSkippingRecordedGaps() {
        val cursor = ReplayCursor(listOf(StateSample(1, valid = true), StateSample(2), StateSample(100, valid = true)))
        assertEquals(2, cursor.sampleAt(cursor.progressAtSecond(2f))?.elapsedSeconds)
        assertFalse(cursor.sampleAt(cursor.progressAtSecond(2f))!!.valid)
        assertEquals(100, cursor.sampleAt(cursor.progressAtSecond(90f))?.elapsedSeconds)
        assertEquals(1, cursor.sampleAt(cursor.progressAtSecond(-10f))?.elapsedSeconds)
        assertEquals(100, cursor.sampleAt(cursor.progressAtSecond(1000f))?.elapsedSeconds)
    }

    @Test fun everyRecordedSecondRoundTripsThroughProgress() {
        val cursor = ReplayCursor((1..600).map { StateSample(it) })
        (1..600).forEach { assertEquals(it, cursor.sampleAt(cursor.progressAtSecond(it.toFloat()))?.elapsedSeconds) }
        assertEquals(0f, ReplayCursor(emptyList()).progressAtSecond(5f))
        assertEquals(0f, ReplayCursor(listOf(StateSample(5))).progressAtSecond(5f))
    }
}
