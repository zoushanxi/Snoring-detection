# SnoreGuard 方案说明书

> 版本：1.0 · 语言：Kotlin · 平台：Android (API 26+)

---

## 1. 项目背景与目标

### 背景

打鼾（鼾症）是常见的睡眠问题，严重时可能与阻塞性睡眠呼吸暂停（OSA）相关，影响本人及同住者的睡眠质量。用户希望在不依赖专业设备的前提下，利用手机麦克风对夜间睡眠过程进行实时监测，记录疑似打鼾事件。

### 目标

- 在 Android 手机上实现**本地**实时音频分析，检测疑似打鼾事件
- 以**前台服务**保证夜间后台运行稳定性
- **保存事件音频片段**以供回放与验证
- 提供事件时间轴记录与统计，并支持导出
- 所有处理均在设备本地完成，不上传云端，保护用户隐私

---

## 2. 当前功能列表

| # | 功能 | 说明 |
|---|------|------|
| 1 | 开始 / 停止检测 | 主界面按钮控制 ForegroundService 的启停 |
| 2 | 实时状态展示 | 显示当前 RMS 能量值与低频能量占比 |
| 3 | 疑似打鼾提示 | 当检测条件满足时，UI 实时显示"疑似打鼾" |
| 4 | 自适应噪声底 | 使用指数滑动平均估计环境噪声，动态调整触发阈值 |
| 5 | 事件切分 | 基于最小持续时长过滤短暂噪声，形成完整打鼾事件 |
| 6 | 录音权限管理 | 使用 `ActivityResultContracts` 动态申请 `RECORD_AUDIO` |
| 7 | 前台服务通知 | 常驻通知栏提醒用户检测正在进行中 |
| 8 | 实时数据总线 | 通过 `LiveData`（`SnoreUiBus`）将 Service 指标推送到 UI |

---

## 3. 技术栈与版本

| 类别 | 具体内容 |
|------|---------|
| **操作系统** | Android API 26+（Android 8.0 Oreo）及以上，面向最新版本（API 35） |
| **开发语言** | Kotlin |
| **UI 框架** | Jetpack Compose（目标架构；MVP 阶段使用传统 XML View） |
| **音频采集** | `android.media.AudioRecord`，PCM 16-bit，单声道，16 kHz |
| **频率分析** | Goertzel 算法（轻量多频点功率估计，替代完整 FFT） |
| **并发模型** | `Thread`（采集循环）+ `LiveData.postValue()`（跨线程 UI 更新） |
| **前台服务** | `Service` + `startForeground()`，前台服务类型：`microphone` |
| **通知渠道** | `NotificationChannel`（Android 8.0+） |
| **依赖管理** | Gradle（Kotlin DSL） |
| **核心 Jetpack 库** | `lifecycle-livedata-ktx`、`lifecycle-runtime-ktx`、`appcompat`、`core-ktx` |
| **最低 SDK** | 26 |
| **目标 SDK** | 35 |

---

## 4. 关键模块 / 包结构说明

```
com.example.snoreguard
├── MainActivity.kt           # 主 Activity：UI 入口，权限申请，服务启停
├── SnoreDetectionService.kt  # ForegroundService：音频采集 + 检测核心逻辑
└── SnoreUiBus.kt             # 单例数据总线：Service → UI 的 LiveData 桥
```

### 4.1 `MainActivity`

- 入口 Activity，托管主界面（开始/停止按钮、状态文本、实时指标）
- 负责动态申请 `RECORD_AUDIO` 权限（`ActivityResultContracts.RequestPermission`）
- 观察 `SnoreUiBus.metrics` LiveData，将实时数据渲染到界面
- 通过 `ContextCompat.startForegroundService()` 启动 / `stopService()` 停止服务

### 4.2 `SnoreDetectionService`

- 继承自 `Service`，在 `onCreate()` 时立即调用 `startForeground()` 进入前台
- 独立工作线程（`Thread`）持续运行音频采集循环，直到 `running = false`
- 采集循环中实现：
  - 滑动帧缓冲区（环形缓冲，帧长 512，帧移 256）
  - 每帧计算 RMS 能量
  - 每帧调用 `goertzelPower()` 估计 6 个低频点（80–400 Hz）与 4 个高频点（800–3000 Hz）的功率
  - 低频能量占比 > 0.72 且总能量超过自适应阈值 → 判定为打鼾候选帧
  - 事件切分：候选帧连续触发/结束时记录事件起止，丢弃短于 800 ms 的事件
- 通过 `SnoreUiBus.post()` 将每帧 `Metrics` 推送给 UI

### 4.3 `SnoreUiBus`

- Kotlin `object`（单例），持有 `MutableLiveData<Metrics>`
- `post(m: Metrics)` 使用 `postValue()` 在任意线程安全地更新值
- `Metrics` 数据类包含：`rms: Double`、`lowBandRatio: Double`、`isSnoreLike: Boolean`

---

## 5. 音频采集与权限 / 前台服务策略

### 5.1 权限声明（`AndroidManifest.xml`）

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<!-- Android 14+ 细分前台服务类型 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
```

Service 声明时需指定 `android:foregroundServiceType="microphone"`，以满足 Android 14（API 34）起的强制要求。

### 5.2 AudioRecord 配置

| 参数 | 值 |
|------|----|
| 音频源 | `MediaRecorder.AudioSource.VOICE_RECOGNITION`（针对语音优化，减少 AGC 干扰） |
| 采样率 | 16,000 Hz |
| 声道 | `CHANNEL_IN_MONO` |
| 格式 | `ENCODING_PCM_16BIT` |
| 缓冲区 | `max(minBufferSize, sampleRate)` — 至少 1 秒数据 |

### 5.3 前台服务策略

- `onStartCommand()` 返回 `START_STICKY`：被系统杀死后自动重启
- `onDestroy()` 中设置 `running = false` 并等待工作线程结束（最多 500 ms），再释放 `AudioRecord`
- 通知渠道优先级设为 `IMPORTANCE_LOW`（不打扰用户睡眠）

---

## 6. 打鼾检测算法现状

### 6.1 预处理与分帧

- **帧长**：512 采样点 ≈ 32 ms（16 kHz）
- **帧移**：256 采样点 ≈ 16 ms（50% 重叠）
- 原始 `Short` 采样归一化到 `[-1.0, 1.0]` 后送入分析

### 6.2 特征提取

#### RMS 能量

$$
\text{RMS} = \sqrt{\frac{1}{N}\sum_{i=0}^{N-1} x_i^2}
$$

反映当前帧的整体响度。

#### Goertzel 频带能量

使用 **Goertzel 算法**（比 FFT 轻量）估计特定频点功率，避免全频谱计算：

- **低频目标频点**：80 Hz, 120 Hz, 180 Hz, 240 Hz, 320 Hz, 400 Hz
- **高频参照频点**：800 Hz, 1200 Hz, 2000 Hz, 3000 Hz

**低频能量占比**：

$$
R_{\text{low}} = \frac{\sum P_{\text{low}}}{\sum P_{\text{low}} + \sum P_{\text{high}} + \varepsilon}
$$

打鼾声音频谱能量集中在 60–500 Hz 低频区域，$R_{\text{low}}$ 较高；环境噪声或说话声的高频成分相对更多。

### 6.3 判定逻辑

```
isSnoreLike  = (lowBandRatio > 0.72)
energyOk     = (rms > noiseFloor + 0.015)
candidate    = isSnoreLike AND energyOk
```

### 6.4 自适应噪声底

$$
\text{noiseFloor}_{t} = \alpha \cdot \text{noiseFloor}_{t-1} + (1-\alpha) \cdot \text{RMS}_{t}
$$

其中 $\alpha = 0.995$（慢速追踪），确保环境噪声变化时阈值自动跟随，减少误报。

### 6.5 事件切分逻辑

```
if candidate AND NOT active:
    active = true; startMs = now           // 事件开始
if NOT candidate AND active:
    duration = now - startMs
    active = false
    if duration >= 800ms:
        record event                        // 有效事件
    else:
        discard                             // 短暂噪声，丢弃
```

最小事件时长 800 ms，过滤瞬态噪声（咳嗽、翻身等）。

---

## 7. 数据存储与导出 / 回放

> **MVP 当前状态**：事件仅通过 `SnoreUiBus` 实时传递到 UI 并在 Logcat 输出，尚未持久化到数据库。

### 7.1 计划方案（待实现）

| 组件 | 技术选型 | 说明 |
|------|---------|------|
| 事件元数据 | **Room / SQLite** | 存储事件开始时间、结束时间、持续时长、最大 RMS、低频占比均值 |
| 音频片段 | **文件系统**（App 私有目录） | 事件前后各 2–3 秒 PCM/WAV，存入 `getExternalFilesDir()` |
| 导出 | **CSV / JSON** | 通过 `FileProvider` + `ShareSheet` 分享或保存到 Downloads |
| 回放 | **MediaPlayer** | 加载本地音频文件回放片段 |

### 7.2 音频片段保留策略

用户在第二次对话中明确要求保留音频，因此：
- 每个有效事件（≥ 800 ms）触发时，将事件前后共约 5 秒的 PCM 数据写入 WAV 文件
- 文件命名格式：`snore_YYYYMMDD_HHmmss.wav`
- 存储于 `Context.getExternalFilesDir("snore_clips")` 私有目录，无需额外存储权限（Android 10+）

---

## 8. 性能与电量注意事项

| 注意点 | 当前策略 |
|--------|---------|
| CPU 占用 | Goertzel 算法仅计算 10 个频点，远低于完整 FFT；帧处理耗时约 1–2 ms |
| 内存 | 工作缓冲区固定大小（约 8 KB），无动态扩展 |
| 电量 | `VOICE_RECOGNITION` 音频源利用 DSP 硬件降噪，减少 CPU 干预；通知级别 `IMPORTANCE_LOW` 减少唤醒 |
| 线程策略 | 单一后台线程持续运行，避免频繁线程创建/销毁 |
| WakeLock | 当前未申请 `WakeLock`（依赖前台服务保活）；如果设备深度睡眠导致采集停止，可补充 `PARTIAL_WAKE_LOCK` |
| 音频焦点 | 当前未请求音频焦点（不需要，仅录音不播放）；如后续加回放需处理焦点冲突 |

---

## 9. 已知问题与改进方向

### 已知问题

1. **无持久化存储**：当前事件仅在内存中存在，重启 App 后丢失
2. **固定阈值**：`lowRatio > 0.72` 是经验值，不同手机麦克风特性差异可能导致误报/漏报
3. **无 UI 历史记录**：无事件列表页，无法查看历史
4. **无音频保存实现**：对话中要求保留音频，但代码尚未实现 WAV 写入
5. **WakeLock 缺失**：部分设备深度睡眠时前台服务可能被限流

### 改进方向

| 优先级 | 方向 |
|--------|------|
| 高 | Room 数据库持久化事件元数据 |
| 高 | WAV 文件写入（保存音频片段） |
| 高 | 事件列表 + 统计页（Jetpack Compose） |
| 中 | 导出 CSV / JSON（FileProvider + ShareSheet） |
| 中 | 参数自适应校准（入睡前 1 分钟校准环境噪声底） |
| 中 | PARTIAL_WAKE_LOCK 防止深度睡眠中断采集 |
| 低 | TFLite 模型替换启发式算法（更高精度） |
| 低 | 睡眠分期（结合加速度计/摄像头） |
| 低 | 云端账号 + 多设备同步（二期） |
