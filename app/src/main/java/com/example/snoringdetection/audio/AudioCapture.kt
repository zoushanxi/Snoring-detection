package com.example.snoringdetection.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * PCM 音频采集封装，基于 [AudioRecord]。
 *
 * ## 参数说明
 * @param sampleRate  采样率（Hz），默认 16000。鼾声频段较低，16 kHz 已足够。
 * @param frameSize   每次读取的采样点数，默认 1024（约 64 ms @ 16 kHz）。
 *
 * ## 注意事项
 * - 调用前必须已授予 RECORD_AUDIO 权限，否则 [AudioRecord] 初始化会失败。
 * - 在前台服务中调用，避免被系统后台限制麦克风访问（Android 9+）。
 * - [audioFrames] 是冷流（cold flow），每次 collect 都会新建一个 AudioRecord 实例。
 */
class AudioCapture(
    val sampleRate: Int = 16_000,
    val frameSize: Int = 1024
) {
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    /**
     * 音频帧流：每个元素是一个归一化的 [FloatArray]，长度为 [frameSize]。
     *
     * PCM 16-bit 有符号整数值范围 [-32768, 32767] 被归一化到 [-1.0, 1.0]。
     *
     * 使用 [flowOn(Dispatchers.IO)] 确保录音循环在 IO 线程中运行。
     */
    val audioFrames: Flow<FloatArray> = flow {
        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        // buffer 至少为 frameSize 的 4 倍，避免溢出
        val bufSize = maxOf(minBufSize, frameSize * 4)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufSize
        )

        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord 初始化失败，请检查 RECORD_AUDIO 权限是否已授予"
        }

        val shortBuffer = ShortArray(frameSize)
        recorder.startRecording()

        try {
            while (coroutineContext.isActive) {
                val readCount = recorder.read(shortBuffer, 0, frameSize)
                if (readCount > 0) {
                    // 将 16-bit PCM 归一化为 Float [-1, 1]
                    val floatFrame = FloatArray(readCount) { i ->
                        shortBuffer[i] / 32768f
                    }
                    emit(floatFrame)
                }
            }
        } finally {
            // 协程被取消时确保释放 AudioRecord 资源
            recorder.stop()
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)
}
