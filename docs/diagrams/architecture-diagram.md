# 架构 / 模块图

SnoreGuard 应用的模块关系与数据流向。

## 架构总览

```mermaid
graph TB
    subgraph UI["UI 层 (MainActivity)"]
        direction TB
        BTN_START["开始/停止按钮"]
        TV_STATUS["状态文本 tvStatus"]
        TV_METRICS["指标文本 tvMetrics"]
    end

    subgraph BUS["数据总线 (SnoreUiBus)"]
        LIVEDATA["LiveData&lt;Metrics&gt;\n(rms, lowBandRatio, isSnoreLike)"]
    end

    subgraph SERVICE["Service 层 (SnoreDetectionService · ForegroundService)"]
        direction TB
        LOOP["采集循环 loop()\n(独立 Thread)"]
        RING["环形帧缓冲\n(frameSize=512, hop=256)"]
        RMS_CALC["RMS 能量计算"]
        GOERTZEL["Goertzel 频点功率\n(10 个频点)"]
        RATIO["低频能量占比\nlowBandRatio = ΣPlow / (ΣPlow+ΣPhigh)"]
        NOISE_FLOOR["自适应噪声底\n(EMA, α=0.995)"]
        STATE_MACHINE["事件状态机\n(min 800ms)"]
        NOTIF["前台通知\n(NotificationChannel)"]
    end

    subgraph AUDIO["音频采集层 (AudioRecord)"]
        AR["AudioRecord\nPCM 16-bit · 16kHz · Mono\nSource: VOICE_RECOGNITION"]
    end

    subgraph STORAGE["存储层 (待实现)"]
        ROOM["Room Database\n事件元数据"]
        FILES["本地文件系统\n音频片段 .wav"]
        EXPORT["CSV / JSON 导出\n(FileProvider)"]
    end

    subgraph SYS["Android 系统"]
        PERM["RECORD_AUDIO\n权限"]
        FG_PERM["FOREGROUND_SERVICE\n_MICROPHONE 权限"]
        WAKE["WakeLock\n(计划)"]
    end

    %% 用户交互流
    BTN_START -->|"startForegroundService()"| SERVICE
    BTN_START -->|"stopService()"| SERVICE

    %% 权限流
    UI -->|"申请"| PERM
    PERM -->|"授予后启动"| SERVICE
    FG_PERM --> SERVICE

    %% 音频采集流
    LOOP -->|"audioRecord.read()"| AR
    AR -->|"ShortArray PCM"| RING

    %% 帧分析流
    RING -->|"满帧(512)"| RMS_CALC
    RING -->|"满帧(512)"| GOERTZEL
    GOERTZEL --> RATIO
    RMS_CALC --> NOISE_FLOOR
    NOISE_FLOOR -->|"动态阈值"| STATE_MACHINE
    RATIO -->|"lowRatio>0.72"| STATE_MACHINE

    %% 事件输出流
    STATE_MACHINE -->|"Metrics"| BUS
    STATE_MACHINE -->|"有效事件(≥800ms)"| STORAGE

    %% UI 更新流
    BUS -->|"LiveData.observe()"| TV_STATUS
    BUS -->|"LiveData.observe()"| TV_METRICS

    %% 前台服务通知
    SERVICE -->|"startForeground()"| NOTIF

    %% 存储内部关系
    ROOM -.->|"查询"| EXPORT
    FILES -.->|"MediaPlayer 回放"| UI

    %% 样式
    classDef layer fill:#f0f4ff,stroke:#4a6cf7,stroke-width:2px
    classDef pending fill:#fff8e1,stroke:#f9a825,stroke-width:2px,stroke-dasharray: 5 5
    class UI,BUS,SERVICE,AUDIO,SYS layer
    class STORAGE pending
```

## 模块职责说明

```mermaid
graph LR
    subgraph 核心模块
        A["MainActivity\n• UI 入口\n• 权限管理\n• 服务启停\n• LiveData 观察"]
        B["SnoreDetectionService\n• ForegroundService\n• 音频采集线程\n• 帧分析\n• 事件状态机"]
        C["SnoreUiBus\n• 单例数据总线\n• MutableLiveData\n• 线程安全 postValue()"]
    end

    subgraph 算法核心
        D["RMS 计算\n• 帧能量 = √(Σx²/N)"]
        E["Goertzel 算法\n• 低频：80~400Hz × 6点\n• 高频：800~3000Hz × 4点"]
        F["自适应噪声底\n• EMA(α=0.995)\n• 动态触发阈值"]
        G["事件状态机\n• 最小持续800ms\n• START/END 记录"]
    end

    subgraph 系统层
        H["AudioRecord\n• VOICE_RECOGNITION\n• 16kHz / PCM16 / Mono"]
        I["Android 权限系统\n• RECORD_AUDIO\n• FOREGROUND_SERVICE\n• ..._MICROPHONE"]
        J["NotificationManager\n• 前台服务通知\n• IMPORTANCE_LOW"]
    end

    A <-->|"Intent / stopService"| B
    B -->|"post(Metrics)"| C
    C -->|"observe()"| A
    B --> D & E
    D --> F
    E --> G
    F --> G
    B <-->|"read() / release()"| H
    A -->|"申请"| I
    B -->|"startForeground()"| J
```

## 数据流向说明

| 数据 | 来源 → 目标 | 传输方式 |
|------|------------|---------|
| PCM 采样 | `AudioRecord` → 帧缓冲 | `audioRecord.read()` 同步写入 |
| 帧分析结果 | `SnoreDetectionService` → `SnoreUiBus` | `SnoreUiBus.post()` (`postValue`) |
| UI 指标更新 | `SnoreUiBus` → `MainActivity` | `LiveData.observe()` 主线程回调 |
| 事件记录（待实现） | `SnoreDetectionService` → Room DB | Coroutine / Room DAO |
| 音频片段（待实现） | 帧缓冲 → `.wav` 文件 | `FileOutputStream` + WAV 头写入 |
| 导出（待实现） | Room DB → 文件 | FileProvider + Intent.ACTION_SEND |
