package com.example.snoringdetection.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class WavFileWriterTest {

    @Test
    fun `writeClip should generate valid wav header and payload`() {
        val tempDir = Files.createTempDirectory("wav-writer-test").toFile()
        val writer = WavFileWriter(tempDir)
        val samples = shortArrayOf(0, 1000, -1000)

        val file = writer.writeClip(
            samples = samples,
            sampleRate = 16_000,
            timestampMs = 1_700_000_000_000
        )

        assertTrue(file.exists())
        val bytes = file.readBytes()
        assertEquals(44 + samples.size * 2, bytes.size)
        assertEquals("RIFF", String(bytes.copyOfRange(0, 4)))
        assertEquals("WAVE", String(bytes.copyOfRange(8, 12)))
        assertEquals("fmt ", String(bytes.copyOfRange(12, 16)))
        assertEquals("data", String(bytes.copyOfRange(36, 40)))
        assertEquals(16_000, bytes.readIntLE(24))
        assertEquals(samples.size * 2, bytes.readIntLE(40))

        file.delete()
        tempDir.deleteRecursively()
    }

    private fun ByteArray.readIntLE(offset: Int): Int =
        ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
}
