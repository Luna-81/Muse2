package com.blue.hush

import com.blue.hush.session.EegNoticeTracker
import com.blue.hush.session.EegSignalStatus.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EegNoticeTrackerTest {
    @Test fun briefLossStaysQuietAndSustainedLossWarnsAtTwentySeconds() {
        val tracker = EegNoticeTracker()
        for (second in 1..19) assertNull(tracker.update(second, LOW_QUALITY))
        assertEquals(LOW_QUALITY, tracker.update(20, LOW_QUALITY))
        assertEquals(LOW_QUALITY, tracker.update(21, UNKNOWN))
    }

    @Test fun recoveryNeedsTwoConsecutiveTrustedSeconds() {
        val tracker = EegNoticeTracker()
        for (second in 1..20) tracker.update(second, MISSING)
        assertEquals(MISSING, tracker.update(21, AVAILABLE))
        assertEquals(MISSING, tracker.update(22, LOW_QUALITY))
        assertEquals(MISSING, tracker.update(23, AVAILABLE))
        assertNull(tracker.update(24, AVAILABLE))
        assertNull(tracker.update(25, LOW_QUALITY))
    }

    @Test fun duplicateUpdatesDoNotAdvanceWarningOrRecovery() {
        val tracker = EegNoticeTracker()
        repeat(30) { assertNull(tracker.update(1, LOW_QUALITY)) }
        for (second in 2..20) tracker.update(second, LOW_QUALITY)
        assertEquals(LOW_QUALITY, tracker.update(21, AVAILABLE))
        assertEquals(LOW_QUALITY, tracker.update(21, AVAILABLE))
        assertNull(tracker.update(22, AVAILABLE))
    }

    @Test fun skippedSecondsDoNotCountAsTrustedRecovery() {
        val tracker = EegNoticeTracker()
        for (second in 1..20) tracker.update(second, UNKNOWN)
        tracker.update(21, AVAILABLE)
        assertEquals(UNKNOWN, tracker.update(23, AVAILABLE))
        assertNull(tracker.update(24, AVAILABLE))
    }

    @Test fun resetClearsWarningsAndAcceptsNewSessionTime() {
        val tracker = EegNoticeTracker()
        for (second in 1..20) tracker.update(second, LOW_QUALITY)
        tracker.reset()
        assertNull(tracker.update(1, MISSING))
        tracker.reset()
        assertNull(tracker.update(100, LOW_QUALITY))
    }
}
