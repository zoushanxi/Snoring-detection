package com.example.snoringdetection.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.snoringdetection.MainActivity
import com.example.snoringdetection.R
import com.example.snoringdetection.audio.AudioCapture
import com.example.snoringdetection.data.SnoringDatabase
import com.example.snoringdetection.data.SnoringEvent
import com.example.snoringdetection.data.SnoringRepository
import com.example.snoringdetection.detection.DetectionResult
import com.example.snoringdetection.detection.SnoringDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 鼾声检测前台服务。
 *
 * ## 关键设计
 * - 使用 [Binder] 允许 Activity 绑定服务，获取实时检测状态。
 * - [_detectionState] 是可观察的状态流，UI 层通过 [detectionState] 订阅。
 * - 前台服务通知必须在 [onStartCommand] 中立即调用 [startForeground]，
 *   否则在 Android 12+ 上会抛出 ForegroundServiceDidNotStartInTimeException。
 *
 * ## 前台服务注意事项（Android 版本差异）
 * - Android 8.0 (API 26)：必须使用通知渠道（NotificationChannel）。
 * - Android 10 (API 29)：访问麦克风的前台服务需在 Manifest 声明
 *   `android:foregroundServiceType="microphone"`。
 * - Android 14 (API 34)：还需在 Manifest 声明
 *   `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />`。
 */
class SnoringDetectionService : Service() {

    // ---- 通知相关常量 ----
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "snoring_detection_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.snoringdetection.START"
        const val ACTION_STOP = "com.example.snoringdetection.STOP"
    }

    // ---- Binder（用于 Activity 绑定） ----
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): SnoringDetectionService = this@SnoringDetectionService
    }

    // ---- 协程作用域 ----
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var detectionJob: Job? = null

    // ---- 核心组件 ----
    private val audioCapture = AudioCapture()
    private val detector = SnoringDetector()
    private lateinit var repository: SnoringRepository

    // ---- 对外可观察状态 ----
    private val _detectionState = MutableStateFlow<DetectionState>(DetectionState.Idle)
    val detectionState: StateFlow<DetectionState> = _detectionState.asStateFlow()

    private val _latestResult = MutableStateFlow<DetectionResult?>(null)
    val latestResult: StateFlow<DetectionResult?> = _latestResult.asStateFlow()

    private val _todayCount = MutableStateFlow(0)
    val todayCount: StateFlow<Int> = _todayCount.asStateFlow()

    // ---- Service 生命周期 ----

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val db = SnoringDatabase.getInstance(applicationContext)
        repository = SnoringRepository(db.snoringEventDao())

        // 订阅今日事件数量更新
        serviceScope.launch {
            repository.getTodayEvents().collect { events ->
                _todayCount.value = events.size
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startDetection()
            ACTION_STOP -> stopDetection()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopDetection()
        super.onDestroy()
    }

    // ---- 检测控制 ----

    /**
     * 启动音频采集与鼾声检测循环。
     * 调用此方法前需确保已获得 RECORD_AUDIO 权限。
     */
    fun startDetection() {
        if (_detectionState.value is DetectionState.Running) return

        // 立即提升为前台服务（必须在 startForeground 调用之前，否则 Android 12+ 会 ANR）
        startForeground(NOTIFICATION_ID, buildNotification("正在监听..."))
        _detectionState.value = DetectionState.Running
        detector.reset()

        detectionJob = serviceScope.launch {
            audioCapture.audioFrames.collect { frame ->
                val result = detector.process(frame)
                _latestResult.value = result

                // 更新通知（可降低频率以节省资源，这里每帧更新）
                updateNotification(result)

                // 若检测到完整鼾声事件，持久化到数据库
                result.newEvent?.let { snoreEvent ->
                    repository.insertEvent(
                        SnoringEvent(
                            timestampMs = snoreEvent.timestampMs,
                            durationMs = snoreEvent.durationMs,
                            peakDb = snoreEvent.peakDb
                        )
                    )
                }
            }
        }
    }

    /**
     * 停止检测，取消协程，退出前台服务。
     */
    fun stopDetection() {
        detectionJob?.cancel()
        detectionJob = null
        detector.reset()
        _detectionState.value = DetectionState.Idle
        _latestResult.value = null
        // stopForeground(int) with STOP_FOREGROUND_REMOVE 是 API 33+ 新 API
        // API 26–32 使用旧的 stopForeground(boolean)（已在 API 33 废弃但仍兼容）
        @Suppress("DEPRECATION")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
    }

    // ---- 通知管理 ----

    /**
     * 创建通知渠道（Android 8.0+ 必须，低版本系统会忽略）。
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "鼾声检测",
            NotificationManager.IMPORTANCE_LOW // LOW 不会有声音，适合持续通知
        ).apply {
            description = "后台持续监听麦克风，检测鼾声"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("睡眠鼾声检测")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_snoring_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 用户无法从通知栏滑动删除
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(result: DetectionResult) {
        val status = if (result.isSnoringFrame) "🔊 检测到鼾声！" else "👂 正在监听..."
        val text = "$status  ${result.db.toInt()} dB"
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}

/** 服务运行状态。 */
sealed class DetectionState {
    /** 空闲，未在检测。 */
    object Idle : DetectionState()

    /** 正在运行检测。 */
    object Running : DetectionState()
}
