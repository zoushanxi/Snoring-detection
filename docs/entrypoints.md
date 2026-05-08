# 入口文档（程序入口与关键启动流程）

## 应用入口

- Activity 入口：`app/src/main/java/com/example/snoringdetection/MainActivity.kt`
- 启动组件：`MainActivity` 中按钮触发 `MainViewModel.startDetection()`

## 检测启动流程

1. 用户点击“开始检测”；
2. 检查/申请 `RECORD_AUDIO`（Android 13+ 包含通知权限）；
3. 发送 `ACTION_START` 到 `SnoringDetectionService`；
4. 服务 `startForeground` 后开启音频帧收集与检测循环。

## 检测停止流程

1. 用户点击“停止检测”；
2. `MainViewModel.stopDetection()` 调用服务停止；
3. 服务取消协程、重置检测器、停止前台服务并 `stopSelf()`。

## 参考

- [时序图](./diagrams/sequence-diagram.md)
