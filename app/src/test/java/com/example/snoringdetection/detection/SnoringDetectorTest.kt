package com.example.snoringdetection.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SnoringDetectorTest {

    @Test
    fun `process should create event when snoring frames end`() {
        val detector = SnoringDetector(
            sampleRate = 16_000,
            frameSize = 1024,
            rmsThreshold = 0.01f,
            lowBandRatio = 0.3f,
            minConsecutiveHits = 3
        )

        val snoreLike = sineFrame(freqHz = 200f, amplitude = 0.25f)
        detector.process(snoreLike, nowMs = 0)
        detector.process(snoreLike, nowMs = 100)
        val hitResult = detector.process(snoreLike, nowMs = 200)
        assertTrue(hitResult.isSnoringFrame)

        detector.process(snoreLike, nowMs = 300)
        detector.process(snoreLike, nowMs = 400)
        val endResult = detector.process(FloatArray(1024), nowMs = 500)

        assertFalse(endResult.isSnoringFrame)
        assertNotNull(endResult.newEvent)
        assertEquals(200L, endResult.newEvent?.timestampMs)
        assertEquals(300L, endResult.newEvent?.durationMs)
    }

    @Test
    fun `process should not trigger on high frequency dominant frame`() {
        val detector = SnoringDetector(
            sampleRate = 16_000,
            frameSize = 1024,
            rmsThreshold = 0.01f,
            lowBandRatio = 0.4f,
            minConsecutiveHits = 2
        )

        val highFreq = sineFrame(freqHz = 2_000f, amplitude = 0.4f)
        repeat(5) { idx ->
            val result = detector.process(highFreq, nowMs = idx * 100L)
            assertFalse(result.isSnoringFrame)
            assertNull(result.newEvent)
        }
    }

    @Test
    fun `reset should clear pending state`() {
        val detector = SnoringDetector(minConsecutiveHits = 2)
        val snoreLike = sineFrame(freqHz = 150f, amplitude = 0.3f)
        detector.process(snoreLike, nowMs = 0)
        detector.process(snoreLike, nowMs = 100)

        detector.reset()
        val resultAfterReset = detector.process(FloatArray(1024), nowMs = 200)

        assertFalse(resultAfterReset.isSnoringFrame)
        assertNull(resultAfterReset.newEvent)
    }

    private fun sineFrame(freqHz: Float, amplitude: Float, size: Int = 1024, sampleRate: Int = 16_000): FloatArray {
        return FloatArray(size) { i ->
            (amplitude * sin(2.0 * PI * freqHz * i / sampleRate)).toFloat()
        }
    }
}
