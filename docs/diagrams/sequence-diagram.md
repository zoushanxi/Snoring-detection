# 运行时序图

用户点击"开始检测"到 UI 显示检测结果的完整交互流程。

## 时序图

```mermaid
sequenceDiagram
    actor User as 用户
    participant MA as MainActivity
    participant SYS as Android 系统
    participant SDS as SnoreDetectionService
    participant AR as AudioRecord
    participant BUS as SnoreUiBus

    User->>MA: 点击「开始检测」
    MA->>SYS: 检查 RECORD_AUDIO 权限
    alt 权限未授予
        SYS-->>User: 弹出权限申请对话框
        User->>SYS: 用户点击「允许」
        SYS-->>MA: onRequestPermissionsResult(GRANTED)
    end

    MA->>SYS: startForegroundService(SnoreDetectionService)
    SYS->>SDS: onCreate()
    SDS->>SYS: startForeground(notification)
    Note over SDS,SYS: 通知栏出现「SnoreGuard 正在检测」

    SYS->>SDS: onStartCommand()
    SDS->>SDS: running = true
    SDS->>SDS: 启动工作线程 loop()

    loop 持续采集（每 ~16 ms 一帧）
        SDS->>AR: audioRecord.read(shortBuf)
        AR-->>SDS: PCM 数据（ShortArray）
        SDS->>SDS: 归一化 Short → Double [-1,1]
        SDS->>SDS: 填充滑动帧缓冲（环形缓冲，帧长 512）

        alt 缓冲区已满（满帧）
            SDS->>SDS: 计算 RMS 能量
            SDS->>SDS: goertzelPower() × 10 个频点
            SDS->>SDS: 计算低频能量占比 lowBandRatio
            SDS->>SDS: 更新自适应噪声底 noiseFloor（α=0.995）
            SDS->>SDS: 判定：energyOk AND lowRatio>0.72?

            alt 打鼾候选帧 AND 未处于事件中
                SDS->>SDS: snoreActive=true; 记录 startMs
                SDS->>BUS: post(Metrics(rms, ratio, isSnoreLike=true))
            else 非候选帧 AND 事件进行中
                SDS->>SDS: duration = now - startMs
                alt duration >= 800ms
                    SDS->>SDS: 记录有效事件（LOG + 待持久化）
                else duration < 800ms
                    SDS->>SDS: 丢弃（短暂噪声）
                end
                SDS->>BUS: post(Metrics(rms, ratio, isSnoreLike=false))
            else 正常帧
                SDS->>BUS: post(Metrics(rms, ratio, isSnoreLike))
            end

            SDS->>SDS: 帧移（保留后 256 采样，ringFill=256）
        end
    end

    BUS-->>MA: LiveData.observe → onChanged(metrics)
    MA->>MA: 更新 tvStatus / tvMetrics

    User->>MA: 点击「停止检测」
    MA->>SYS: stopService(SnoreDetectionService)
    SYS->>SDS: onDestroy()
    SDS->>SDS: running = false
    SDS->>SDS: worker.join(500ms)
    SDS->>AR: audioRecord.stop() / release()
    SDS-->>MA: 服务销毁完成
    MA->>MA: tvStatus = "状态：已停止"
```

## 说明

| 步骤 | 关键点 |
|------|--------|
| 权限检查 | 使用 `ActivityResultContracts.RequestPermission` 异步申请，仅在已授权时启动服务 |
| 前台服务启动 | `ContextCompat.startForegroundService()` 适配 Android 8.0+；服务类型声明为 `microphone`（Android 14+ 必需） |
| 采集循环 | 独立 `Thread` 持续运行，每次 `audioRecord.read()` 获取一批采样，按 hopSize=256 切片推入环形缓冲 |
| 帧分析 | 满帧（512 采样）后触发 Goertzel 频率分析，结果驱动状态机判定事件开始/结束 |
| 数据总线 | `SnoreUiBus.post()` 使用 `postValue()` 确保从工作线程安全更新 LiveData，Activity 在主线程回调 |
| 服务停止 | `running = false` 后工作线程在下一次循环检查时自然退出，资源有序释放 |
