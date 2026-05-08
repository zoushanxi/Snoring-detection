# 接口文档

## 1. 检测服务接口（`SnoringDetectionService`）

- `startDetection()`：开始检测并进入前台服务。
- `stopDetection()`：停止检测、清理状态并退出前台服务。
- `detectionState: StateFlow<DetectionState>`：服务运行状态。
- `latestResult: StateFlow<DetectionResult?>`：实时检测结果。
- `todayCount: StateFlow<Int>`：今日事件数。

## 2. 检测算法接口（`SnoringDetector`）

- `process(samples: FloatArray, nowMs: Long): DetectionResult`
  - 输入：归一化 PCM 帧
  - 输出：分贝、RMS、低频占比、是否处于鼾声、是否产出新事件
- `reset()`：重置内部状态。

## 3. 数据层接口

- `SnoringEventDao.insert(event)`
- `SnoringEventDao.getTodayEvents(dayStartMs)`
- `SnoringEventDao.getRecentEvents(limit)`
- `SnoringRepository` 对 DAO 的封装：`insertEvent/getTodayEvents/getRecentEvents/deleteAll`

## 4. 音频写入接口（`WavFileWriter`）

- `writeClip(samples, sampleRate, timestampMs): File`
  - 输入：PCM16 单声道采样
  - 输出：WAV 文件对象
