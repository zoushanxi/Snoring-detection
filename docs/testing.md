# 测试文档

## 测试目标

覆盖可单测的关键逻辑：
- 检测算法阈值与事件切分；
- 音频片段 WAV 写入正确性。

## 已集成测试

### 1) `SnoringDetectorTest`
路径：`app/src/test/java/com/example/snoringdetection/detection/SnoringDetectorTest.kt`

覆盖点：
- 连续命中后触发事件，结束后产出 `newEvent`；
- 高频主导帧不应被误判为鼾声；
- `reset()` 能清空内部状态。

### 2) `WavFileWriterTest`
路径：`app/src/test/java/com/example/snoringdetection/audio/WavFileWriterTest.kt`

覆盖点：
- WAV 文件头（RIFF/WAVE/fmt/data）结构正确；
- 采样率和 data chunk size 字段正确；
- 文件大小与样本长度一致。

## 本地执行

```bash
./gradlew test
```

> 说明：当前沙箱环境无法解析 `dl.google.com`，导致 Android Gradle Plugin 依赖不可下载，命令在当前环境无法完整执行；建议在可访问 Google Maven 的环境或 CI 中运行。
