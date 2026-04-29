package com.example.snoringdetection.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snoringdetection.data.SnoringDatabase
import com.example.snoringdetection.data.SnoringEvent
import com.example.snoringdetection.data.SnoringRepository
import com.example.snoringdetection.detection.DetectionResult
import com.example.snoringdetection.service.DetectionState
import com.example.snoringdetection.service.SnoringDetectionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主界面 ViewModel，负责：
 * 1. 绑定 / 解绑 [SnoringDetectionService]
 * 2. 将服务状态代理为可观察的 [StateFlow] 供 Compose UI 使用
 * 3. 提供开始/停止检测的入口
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SnoringRepository

    init {
        val db = SnoringDatabase.getInstance(application)
        repository = SnoringRepository(db.snoringEventDao())
    }

    // ---- 服务绑定 ----
    private var service: SnoringDetectionService? = null
    private var isBound = false

    private val _detectionState = MutableStateFlow<DetectionState>(DetectionState.Idle)
    val detectionState: StateFlow<DetectionState> = _detectionState.asStateFlow()

    private val _latestResult = MutableStateFlow<DetectionResult?>(null)
    val latestResult: StateFlow<DetectionResult?> = _latestResult.asStateFlow()

    private val _todayCount = MutableStateFlow(0)
    val todayCount: StateFlow<Int> = _todayCount.asStateFlow()

    /** 最近 50 条事件，供 UI 列表展示。 */
    val recentEvents: StateFlow<List<SnoringEvent>> = run {
        val flow = MutableStateFlow<List<SnoringEvent>>(emptyList())
        viewModelScope.launch {
            repository.getRecentEvents(50).collect { flow.value = it }
        }
        flow
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? SnoringDetectionService.LocalBinder ?: return
            service = localBinder.getService()
            isBound = true

            // 将服务内部状态转发给 ViewModel 的 StateFlow
            viewModelScope.launch {
                localBinder.getService().detectionState.collect { _detectionState.value = it }
            }
            viewModelScope.launch {
                localBinder.getService().latestResult.collect { _latestResult.value = it }
            }
            viewModelScope.launch {
                localBinder.getService().todayCount.collect { _todayCount.value = it }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
            _detectionState.value = DetectionState.Idle
        }
    }

    /**
     * 绑定服务（在 Activity onStart 时调用）。
     */
    fun bindService(context: Context) {
        val intent = Intent(context, SnoringDetectionService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * 解绑服务（在 Activity onStop 时调用）。
     */
    fun unbindService(context: Context) {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }

    /**
     * 启动前台服务并开始检测。
     * 发送 [SnoringDetectionService.ACTION_START] Intent 启动服务（保证进程存活），
     * 同时绑定以获取实时状态。
     */
    fun startDetection(context: Context) {
        val intent = Intent(context, SnoringDetectionService::class.java).apply {
            action = SnoringDetectionService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    /**
     * 停止检测与前台服务。
     */
    fun stopDetection(context: Context) {
        service?.stopDetection()
            ?: run {
                // 如果没绑定服务，直接发送 stop intent
                val intent = Intent(context, SnoringDetectionService::class.java).apply {
                    action = SnoringDetectionService.ACTION_STOP
                }
                context.startService(intent)
            }
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel 清除时不解绑服务，服务应该继续运行；
        // 解绑在 Activity.onStop 中处理
    }
}
