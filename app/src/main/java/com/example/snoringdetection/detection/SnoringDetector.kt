package com.example.snoringdetection.detection

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 简易鼾声检测器——基于短时能量（RMS/分贝）与频带能量启发式规则。
 *
 * ## 算法说明
 * 鼾声的典型声学特征：
 * 1. **能量较高**：分贝通常 > 40–50 dBFS（相对满量程），本类用 RMS 来估算。
 * 2. **频带集中在低频**：鼾声主频一般在 100–500 Hz 之间（基频及低次谐波）。
 *    本实现用一个简化的 DFT 只计算几个关键频点来估算低频 vs 高频能量比，
 *    避免引入完整 FFT 库依赖，同时满足 MVP 场景。
 * 3. **持续命中**：单帧能量超标可能是偶发噪声；要求连续 [minConsecutiveHits] 帧
 *    都满足条件才判定为一次鼾声事件，减少误报。
 *
 * ## 可调参数
 * @param sampleRate          采样率（Hz），默认 16000
 * @param frameSize           每次送入的 PCM 帧大小（采样数），默认 1024
 * @param rmsThreshold        RMS 阈值（0.0–1.0 归一化范围），超过则视为高能量帧。
 *                            典型值 0.02（约等于 -34 dBFS）。
 * @param lowBandRatio        低频（100–500 Hz）能量占全频带能量的最低比例阈值，
 *                            超过则认为频带特征符合鼾声。默认 0.35。
 * @param minConsecutiveHits  连续满足条件的帧数阈值，达到后触发"鼾声事件"。默认 3。
 */
class SnoringDetector(
    private val sampleRate: Int = 16_000,
    private val frameSize: Int = 1024,
    private val rmsThreshold: Float = 0.02f,
    private val lowBandRatio: Float = 0.35f,
    private val minConsecutiveHits: Int = 3
) {

    // ---- 内部状态 ----

    /** 当前连续命中帧计数 */
    private var consecutiveHits = 0

    /** 当前正在进行中的鼾声事件的开始时间戳（毫秒），-1 表示无正在进行的事件 */
    private var eventStartMs = -1L

    /** 当前帧内的峰值分贝 */
    private var currentPeakDb = -100f

    // ---- 频带定义（DFT 关键频点索引）----

    /**
     * 低频带 [lowBandLow, lowBandHigh] Hz，代表典型鼾声基频范围。
     * 注意：这里只计算几个代表性频点，不做完整 FFT。
     */
    private val lowBandLowHz = 100
    private val lowBandHighHz = 500

    // ---- 公共接口 ----

    /**
     * 处理一帧 PCM 数据（16-bit 有符号整数，归一化为 -1.0 ~ 1.0 的 Float）。
     *
     * @param samples  归一化后的浮点 PCM 采样数组（长度应等于 [frameSize]）
     * @param nowMs    当前帧的时间戳（毫秒），用于事件持续时间计算
     * @return         [DetectionResult]，包含当前分贝、是否正在打呼、是否触发新事件等信息
     */
    fun process(samples: FloatArray, nowMs: Long = System.currentTimeMillis()): DetectionResult {
        val rms = computeRms(samples)
        val db = rmsToDb(rms)
        currentPeakDb = maxOf(currentPeakDb, db)

        val bandRatio = computeLowBandEnergyRatio(samples)
        val isHighEnergy = rms >= rmsThreshold
        val isLowBandDominant = bandRatio >= lowBandRatio
        val frameHit = isHighEnergy && isLowBandDominant

        var newEvent: SnoreEvent? = null

        if (frameHit) {
            consecutiveHits++
            if (consecutiveHits == minConsecutiveHits) {
                // 刚刚触发新事件
                eventStartMs = nowMs
                currentPeakDb = db
            }
        } else {
            if (consecutiveHits >= minConsecutiveHits && eventStartMs > 0) {
                // 事件结束，生成事件记录
                val duration = nowMs - eventStartMs
                newEvent = SnoreEvent(
                    timestampMs = eventStartMs,
                    durationMs = duration,
                    peakDb = currentPeakDb
                )
                eventStartMs = -1L
                currentPeakDb = -100f
            }
            consecutiveHits = 0
        }

        return DetectionResult(
            db = db,
            rms = rms,
            bandRatio = bandRatio,
            isSnoringFrame = frameHit && consecutiveHits >= minConsecutiveHits,
            newEvent = newEvent
        )
    }

    /** 重置内部状态（停止检测时调用）。 */
    fun reset() {
        consecutiveHits = 0
        eventStartMs = -1L
        currentPeakDb = -100f
    }

    // ---- 私有计算工具 ----

    /**
     * 计算帧 RMS（均方根能量），结果在 [0, 1] 范围内（16-bit 归一化输入）。
     */
    private fun computeRms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s
        return sqrt(sum / samples.size).toFloat()
    }

    /**
     * 将 RMS 转换为分贝（dBFS：0 dB = 满量程）。
     * 避免 log(0)：RMS 过小时返回 -100 dB。
     */
    private fun rmsToDb(rms: Float): Float {
        return if (rms < 1e-10f) -100f
        else (20.0 * log10(rms.toDouble())).toFloat()
    }

    /**
     * 使用简化 DFT 计算低频带（[lowBandLowHz]–[lowBandHighHz]）的能量比例。
     *
     * 原理：对若干代表性频点计算 DFT 系数幅度的平方（能量），
     * 低频带能量 / 全频带总能量 = 比例值。
     *
     * 精度：仅为启发式近似，满足 MVP 需求。如需精确分析，可换成完整 FFT。
     *
     * @return 低频带能量比例，范围 [0, 1]
     */
    private fun computeLowBandEnergyRatio(samples: FloatArray): Float {
        val n = samples.size
        if (n == 0) return 0f

        val freqResolution = sampleRate.toFloat() / n

        // 以 50 Hz 为步长采样代表性频点，覆盖 50 Hz ~ sampleRate/2
        // 步长越小精度越高但计算量越大；50 Hz 是能量/性能平衡点
        val freqStep = 50
        var lowEnergy = 0.0
        var totalEnergy = 0.0

        var freqHz = freqStep
        while (freqHz <= sampleRate / 2) {
            val k = (freqHz / freqResolution).toInt().coerceIn(0, n - 1)
            val energy = dftMagnitudeSquared(samples, k)
            totalEnergy += energy
            if (freqHz in lowBandLowHz..lowBandHighHz) {
                lowEnergy += energy
            }
            freqHz += freqStep
        }

        return if (totalEnergy < 1e-20) 0f
        else (lowEnergy / totalEnergy).toFloat().coerceIn(0f, 1f)
    }

    /**
     * 计算单个频点 k 的 DFT 幅度平方（能量）。
     * 公式：|X[k]|² = (Σ x[n]·cos(2π·k·n/N))² + (Σ x[n]·sin(2π·k·n/N))²
     */
    private fun dftMagnitudeSquared(samples: FloatArray, k: Int): Double {
        val n = samples.size
        var re = 0.0
        var im = 0.0
        val twoPiKOverN = 2.0 * Math.PI * k / n
        for (i in samples.indices) {
            val angle = twoPiKOverN * i
            re += samples[i] * Math.cos(angle)
            im -= samples[i] * Math.sin(angle)
        }
        return re * re + im * im
    }

}

/**
 * 每帧检测结果。
 *
 * @param db             当前帧的近似分贝值（dBFS）
 * @param rms            当前帧的 RMS 能量（归一化）
 * @param bandRatio      低频带能量占比
 * @param isSnoringFrame 当前是否处于打呼噜状态（连续命中达标）
 * @param newEvent       若本帧结束一段鼾声，返回该事件；否则为 null
 */
data class DetectionResult(
    val db: Float,
    val rms: Float,
    val bandRatio: Float,
    val isSnoringFrame: Boolean,
    val newEvent: SnoreEvent?
)

/**
 * 一次完整的鼾声事件（尚未持久化到数据库的内存表示）。
 */
data class SnoreEvent(
    val timestampMs: Long,
    val durationMs: Long,
    val peakDb: Float
)
