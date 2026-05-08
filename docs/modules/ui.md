# UI 模块

- 代码路径：`MainActivity.kt`, `ui/MainViewModel.kt`
- 职责：权限申请、服务启停、状态与历史事件展示。
- 与服务交互：通过 `MainViewModel` 绑定 `SnoringDetectionService` 并消费 `StateFlow`。
