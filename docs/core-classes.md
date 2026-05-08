# 核心类 / 核心函数说明

## `SnoringDetector`

- 关键函数：`process(samples, nowMs)`
- 作用：按帧计算 RMS、dBFS、低频能量比，并进行事件切分。
- 关键输出：`DetectionResult.newEvent`（事件结束时产出）。

## `SnoringDetectionService`

- 关键函数：`startDetection()` / `stopDetection()`
- 作用：连接采集、检测、通知、数据库、音频写入。
- 关键逻辑：
  - pre-roll 缓冲管理；
  - 事件结束时写 WAV 并落库。

## `AudioCapture`

- 关键接口：`audioFrames: Flow<FloatArray>`
- 作用：封装 `AudioRecord`，输出归一化浮点帧。

## `WavFileWriter`

- 关键函数：`writeClip(samples, sampleRate, timestampMs)`
- 作用：输出标准 44-byte WAV 头 + PCM 数据，生成可回放文件。
