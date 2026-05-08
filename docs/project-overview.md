# 项目概览 / 方案说明

## 1. 项目目标

本项目是一个 Android 打鼾检测应用，核心目标：
- 基于麦克风实时采集进行本地检测；
- 通过前台服务稳定运行；
- 记录打鼾事件并保留对应音频片段；
- 通过测试覆盖关键检测逻辑，提升回归效率。

## 2. 当前实现范围（对应代码）

- UI：`MainActivity.kt`（Jetpack Compose）
- 检测服务：`service/SnoringDetectionService.kt`
- 音频采集：`audio/AudioCapture.kt`
- 检测算法：`detection/SnoringDetector.kt`
- 音频片段落盘：`audio/WavFileWriter.kt`
- 数据存储：Room（`data/*`）

## 3. 检测流程（简述）

1. 服务从 `AudioCapture.audioFrames` 持续读取音频帧；
2. `SnoringDetector.process()`计算 RMS + 低频能量比；
3. 连续命中后进入鼾声状态，结束时产出 `newEvent`；
4. 服务将事件写入 Room，并将事件对应的 PCM 写为 WAV 文件。

## 4. 关键工程化改动（本轮）

- 补充 JVM 单元测试（检测算法、WAV 写入）；
- 修复并补齐“保留音频”实现；
- 统一 docs 结构并补齐架构、接口、入口、模块、测试文档。

## 5. 相关文档

- [架构文档](./architecture.md)
- [测试文档](./testing.md)
- [模块文档](./modules/README.md)
