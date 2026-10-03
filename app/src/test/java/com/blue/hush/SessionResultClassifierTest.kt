package com.blue.hush

import com.blue.hush.processing.SessionResultClassifier
import com.blue.hush.session.ResultLabel
import com.blue.hush.session.StateSample
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionResultClassifierTest {
    @Test fun newClassificationUsesCalmnessRatherThanStillness() {
        val samples = List(8) { StateSample(it + 1, stillness = 1.0, valid = true, calmness = if (it < 4) 0.3 else 0.7, algorithmVersion = 5) }
        assertEquals(ResultLabel.SETTLING, SessionResultClassifier.classify(samples))
    }
    @Test
    fun classifiesShortSessionFromAvailableSamples() {
        val samples = List(3) { StateSample(it, calmness = 0.8, valid = true, algorithmVersion = 5) }
        assertEquals(ResultLabel.STEADY, SessionResultClassifier.classify(samples))
    }

    @Test
    fun reportsSettlingWhenLaterSamplesAreMoreStable() {
        val samples = List(4) { StateSample(it, calmness = 0.3, valid = true, algorithmVersion = 5) } +
            List(4) { StateSample(it + 4, calmness = 0.7, valid = true, algorithmVersion = 5) }
        assertEquals(ResultLabel.SETTLING, SessionResultClassifier.classify(samples))
    }

    @Test fun missingCalmnessNeverFallsBackToMotion() {
        val samples = List(8) { StateSample(it + 1, stillness = if (it < 4) 0.3 else 0.9, valid = true, algorithmVersion = 5) }
        assertEquals(ResultLabel.STEADY, SessionResultClassifier.classify(samples))
    }
}
