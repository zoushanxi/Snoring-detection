# Snoring-detection

SnoreGuard：Android 打鼾 / 打呼噜检测与睡眠事件记录应用。

利用手机麦克风实时采集音频，通过 Goertzel 频带能量分析与自适应噪声底算法，在本地检测疑似打鼾事件，并以前台服务保证夜间后台稳定运行。

## 文档

详细的方案说明书与架构图请查看 **[docs/](./docs/README.md)**：

| 文档 | 链接 |
|------|------|
| 方案说明书 | [docs/solution-spec.md](./docs/solution-spec.md) |
| 运行时序图 | [docs/diagrams/sequence-diagram.md](./docs/diagrams/sequence-diagram.md) |
| 架构 / 模块图 | [docs/diagrams/architecture-diagram.md](./docs/diagrams/architecture-diagram.md) |