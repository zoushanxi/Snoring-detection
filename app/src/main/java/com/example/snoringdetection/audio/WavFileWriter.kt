package com.example.snoringdetection.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 将 PCM16 单声道采样写入 WAV 文件。
 */
class WavFileWriter(
    private val outputDir: File
) {
    init {
        outputDir.mkdirs()
    }

    fun writeClip(
        samples: ShortArray,
        sampleRate: Int,
        timestampMs: Long
    ): File {
        val fileName = "snore_${FILE_NAME_FORMAT.format(Instant.ofEpochMilli(timestampMs))}.wav"
        val file = File(outputDir, fileName)
        FileOutputStream(file).use { output ->
            val wavHeader = buildHeader(
                dataSizeBytes = samples.size * SHORT_BYTES,
                sampleRate = sampleRate
            )
            output.write(wavHeader)
            val pcmBuffer = ByteBuffer.allocate(samples.size * SHORT_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { pcmBuffer.putShort(it) }
            output.write(pcmBuffer.array())
        }
        return file
    }

    private fun buildHeader(dataSizeBytes: Int, sampleRate: Int): ByteArray {
        val byteRate = sampleRate * CHANNELS * SHORT_BYTES
        val totalDataLen = dataSizeBytes + 36

        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalDataLen)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(CHANNELS.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((CHANNELS * SHORT_BYTES).toShort())
            putShort((SHORT_BYTES * 8).toShort())
            put("data".toByteArray())
            putInt(dataSizeBytes)
        }.array()
    }

    private companion object {
        const val CHANNELS = 1
        const val SHORT_BYTES = 2
        val FILE_NAME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneId.systemDefault())
    }
}
