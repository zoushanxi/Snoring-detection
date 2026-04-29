# SnoreGuard 文档索引

本目录集中存放 SnoreGuard 打鼾检测 Android App 的方案说明书与架构图文档。

## 文档列表

| 文档 | 说明 |
|------|------|
| [方案说明书](./solution-spec.md) | 项目背景、功能列表、技术栈、模块说明、检测算法、存储策略、性能与已知问题 |
| [运行时序图](./diagrams/sequence-diagram.md) | 用户点击开始 → Service 采集 → 帧分析 → 事件切分 → UI 更新的完整时序 |
| [架构 / 模块图](./diagrams/architecture-diagram.md) | UI、Service、Audio、Detection、Storage 各模块关系与数据流向 |

## 快速导航

### 方案说明书章节

- [1. 项目背景与目标](./solution-spec.md#1-项目背景与目标)
- [2. 当前功能列表](./solution-spec.md#2-当前功能列表)
- [3. 技术栈与版本](./solution-spec.md#3-技术栈与版本)
- [4. 关键模块 / 包结构](./solution-spec.md#4-关键模块--包结构说明)
- [5. 音频采集与权限 / 前台服务策略](./solution-spec.md#5-音频采集与权限--前台服务策略)
- [6. 打鼾检测算法现状](./solution-spec.md#6-打鼾检测算法现状)
- [7. 数据存储与导出 / 回放](./solution-spec.md#7-数据存储与导出--回放)
- [8. 性能与电量注意事项](./solution-spec.md#8-性能与电量注意事项)
- [9. 已知问题与改进方向](./solution-spec.md#9-已知问题与改进方向)

### 图表

- [运行时序图（Mermaid）](./diagrams/sequence-diagram.md#时序图)
- [架构总览图（Mermaid）](./diagrams/architecture-diagram.md#架构总览)
- [模块职责图（Mermaid）](./diagrams/architecture-diagram.md#模块职责说明)
