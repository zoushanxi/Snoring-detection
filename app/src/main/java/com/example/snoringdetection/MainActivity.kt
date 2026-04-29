package com.example.snoringdetection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.snoringdetection.data.SnoringEvent
import com.example.snoringdetection.service.DetectionState
import com.example.snoringdetection.ui.MainViewModel
import com.example.snoringdetection.ui.theme.SnoringDetectionTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主界面 Activity。
 *
 * 单页面 Compose UI，包含：
 * - 权限状态提示
 * - 开始/停止检测按钮
 * - 实时分贝与检测状态
 * - 今日事件计数
 * - 最近事件列表
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // 权限申请 launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 权限结果由 Compose 状态驱动，不需要额外处理
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SnoringDetectionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SnoringDetectionScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.bindService(this)
    }

    override fun onStop() {
        super.onStop()
        viewModel.unbindService(this)
    }

    /**
     * 申请必要权限：RECORD_AUDIO + POST_NOTIFICATIONS（Android 13+）。
     */
    fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}

// ============================================================
// Compose UI
// ============================================================

@Composable
fun SnoringDetectionScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    val detectionState by viewModel.detectionState.collectAsState()
    val latestResult by viewModel.latestResult.collectAsState()
    val todayCount by viewModel.todayCount.collectAsState()
    val recentEvents by viewModel.recentEvents.collectAsState()

    // 检查权限状态（每次恢复时刷新）
    var hasAudioPermission by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAudioPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isRunning = detectionState is DetectionState.Running

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- 标题 ----
        Text(
            text = "😴 睡眠鼾声检测",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // ---- 权限状态 ----
        PermissionStatusCard(
            hasAudioPermission = hasAudioPermission,
            onRequestPermission = { activity?.requestPermissions() }
        )

        // ---- 实时状态卡片 ----
        RealtimeStatusCard(
            isRunning = isRunning,
            db = latestResult?.db ?: -100f,
            isSnoringFrame = latestResult?.isSnoringFrame ?: false,
            todayCount = todayCount
        )

        // ---- 开始/停止按钮 ----
        Button(
            onClick = {
                if (isRunning) {
                    viewModel.stopDetection(context)
                } else {
                    if (hasAudioPermission) {
                        viewModel.startDetection(context)
                    } else {
                        activity?.requestPermissions()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) Color(0xFFE53935) else Color(0xFF43A047)
            )
        ) {
            Text(
                text = if (isRunning) "⏹ 停止检测" else "▶ 开始检测",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // ---- 事件列表 ----
        Text(
            text = "最近检测记录",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        if (recentEvents.isEmpty()) {
            Text(
                text = "暂无记录",
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recentEvents) { event ->
                    SnoringEventItem(event)
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasAudioPermission)
                Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasAudioPermission) "✅ 麦克风权限已授予" else "⚠️ 需要麦克风权限",
                    fontWeight = FontWeight.Medium
                )
                if (!hasAudioPermission) {
                    Text(
                        text = "点击右侧按钮授权",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            if (!hasAudioPermission) {
                Button(onClick = onRequestPermission) {
                    Text("授权")
                }
            }
        }
    }
}

@Composable
private fun RealtimeStatusCard(
    isRunning: Boolean,
    db: Float,
    isSnoringFrame: Boolean,
    todayCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !isRunning -> MaterialTheme.colorScheme.surfaceVariant
                isSnoringFrame -> Color(0xFFFFEBEE)
                else -> Color(0xFFE3F2FD)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 检测状态
                Column {
                    Text("检测状态", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(4.dp))
                    val statusText = when {
                        !isRunning -> "⏸ 未开始"
                        isSnoringFrame -> "🔊 检测到鼾声！"
                        else -> "👂 监听中..."
                    }
                    Text(
                        text = statusText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (isSnoringFrame && isRunning) Color(0xFFE53935) else Color.Unspecified
                    )
                }

                // 今日次数
                Column(horizontalAlignment = Alignment.End) {
                    Text("今日检测", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$todayCount 次",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 分贝值
            Column {
                Text("当前分贝", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(4.dp))
                val dbDisplay = if (isRunning) "${db.toInt()} dBFS" else "--"
                Text(
                    text = dbDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }

            // 能量条
            if (isRunning) {
                Spacer(Modifier.height(8.dp))
                val progress = ((db + 60f) / 60f).coerceIn(0f, 1f) // [-60, 0] dBFS → [0,1]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(8.dp)
                            .background(
                                if (isSnoringFrame) Color(0xFFE53935) else Color(0xFF42A5F5),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun SnoringEventItem(event: SnoringEvent) {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeStr = formatter.format(Date(event.timestampMs))
    val durationSec = event.durationMs / 1000.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("🕐 $timeStr", fontWeight = FontWeight.Medium)
                Text(
                    "持续 ${String.format("%.1f", durationSec)}s",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                "${event.peakDb.toInt()} dB",
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE53935)
            )
        }
    }
}
