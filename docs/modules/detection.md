# Detection 模块

- 代码路径：`detection/SnoringDetector.kt`
- 核心能力：
  - RMS + dBFS 计算；
  - 低频能量占比估计；
  - 连续命中与事件切分。
- 输出结构：`DetectionResult`, `SnoreEvent`。
