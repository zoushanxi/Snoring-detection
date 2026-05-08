# 架构文档

## 分层结构

- **UI 层**：Compose 页面展示状态和事件列表。
- **Service 层**：前台服务负责实时采集、检测、事件落库与通知更新。
- **算法层**：`SnoringDetector` 封装阈值判定与事件切分。
- **数据层**：Room + Repository。
- **文件层**：`WavFileWriter` 将事件音频输出到应用目录。

## 关键设计点

1. **前台服务保活**：通过 `startForeground` + `foregroundServiceType="microphone"`。
2. **可测试核心下沉**：检测器和 WAV 写入器均可在 JVM 侧测试。
3. **事件与音频对齐策略**：服务维护 pre-roll 缓冲，在事件结束时输出片段。

## 对应关系

- UI -> `MainActivity`, `ui/MainViewModel`
- Service -> `service/SnoringDetectionService`
- Audio -> `audio/AudioCapture`, `audio/WavFileWriter`
- Detection -> `detection/SnoringDetector`
- Data -> `data/SnoringEvent`, `SnoringEventDao`, `SnoringRepository`, `SnoringDatabase`

## 图示

- [时序图](./diagrams/sequence-diagram.md)
- [模块交互图](./diagrams/architecture-diagram.md)
