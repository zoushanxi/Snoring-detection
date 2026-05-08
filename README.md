# 睡眠鼾声检测 (Snoring Detection)

一个基于 Android 的实时鼾声检测 App，使用麦克风音频采集 + 短时能量与频带特征的启发式算法来识别鼾声片段。

## 文档

详细的方案说明书与架构图请查看 **[docs/](./docs/README.md)**：

| 文档 | 链接 |
|------|------|
| 文档总索引 | [docs/README.md](./docs/README.md) |
| 方案说明书 | [docs/project-overview.md](./docs/project-overview.md) |
| 架构文档 | [docs/architecture.md](./docs/architecture.md) |
| 测试文档 | [docs/testing.md](./docs/testing.md) |
| 运行时序图 | [docs/diagrams/sequence-diagram.md](./docs/diagrams/sequence-diagram.md) |
| 架构 / 模块图 | [docs/diagrams/architecture-diagram.md](./docs/diagrams/architecture-diagram.md) |

---

## 功能特性

- 🎙 **实时麦克风采集**：使用 `AudioRecord` 采集 16 kHz PCM 音频
- 🔊 **鼾声检测算法**：基于 RMS 能量阈值 + 低频带（100–500 Hz）能量比，连续帧命中触发事件
- 🔔 **前台服务**：检测期间保持后台运行，不被系统杀掉
- 💾 **本地存储**：使用 Room 记录每次鼾声事件（时间戳、持续时长、峰值分贝、音频片段路径）
- 🎵 **音频保留**：每次有效鼾声事件会生成可回放的 WAV 片段文件
- 📊 **简洁 UI**：Jetpack Compose 单页面，显示实时分贝、检测状态、今日次数、历史记录

---

## 如何在 Android Studio 中打开和运行

### 前置条件

| 工具 | 推荐版本 |
|------|---------|
| Android Studio | Hedgehog (2023.1.1) 或以上 |
| JDK | 17 |
| Android SDK | API 34 (已安装 Build Tools 34.x) |
| 设备/模拟器 | Android 8.0 (API 26) 或以上 |

### 步骤

1. 克隆仓库：
   ```bash
   git clone https://github.com/f447zoushanxi/Snoring-detection.git
   cd Snoring-detection
   ```

2. 在 Android Studio 中选择 **File → Open**，选中仓库根目录（包含 `settings.gradle.kts` 的目录）。

3. 等待 Gradle Sync 完成（首次需要下载依赖，可能需要几分钟）。

4. 连接设备或启动模拟器（需要 API 26+）。

5. 点击 **Run 'app'**（▶）或使用命令行：
   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

6. 首次启动时，App 会请求 **麦克风权限**（Android 13+ 还需通知权限），授权后点击「开始检测」即可。

> **注意**：模拟器麦克风需要在 AVD 配置中启用 "Enable microphone input"。

---

## 权限说明

| 权限 | 作用 | 申请时机 |
|------|------|---------|
| `RECORD_AUDIO` | 麦克风采集 | 运行时（首次点击「开始检测」） |
| `FOREGROUND_SERVICE` | 声明前台服务 | 安装时自动授予 |
| `FOREGROUND_SERVICE_MICROPHONE` | Android 14+ 细分麦克风前台服务类型 | 安装时自动授予 |
| `POST_NOTIFICATIONS` | 前台服务通知（Android 13+） | 运行时（首次点击「开始检测」） |

---

## 检测算法说明

### 鼾声的声学特征

鼾声通常具有以下两个主要特征：

1. **能量较高**：相比安静环境，鼾声的 RMS 能量明显高于背景噪声。
2. **低频优势**：鼾声基频一般在 100–500 Hz，高次谐波相对较弱。

### 算法流程

```
AudioRecord (16kHz, 16-bit PCM)
      │
      ▼ 每帧 1024 采样（约 64ms）
┌─────────────────────────────┐
│  1. 计算 RMS（均方根能量）     │
│  2. RMS → dBFS 分贝          │
│  3. 简化 DFT 估算低频带能量比  │
└─────────────────────────────┘
      │
      ▼ 判定逻辑
  RMS ≥ 阈值 (0.02)         ← 可调
  AND 低频带占比 ≥ 35%       ← 可调
      │
      ▼ 连续命中 ≥ 3 帧       ← 可调
  → 触发"鼾声事件"
  → 写入 Room 数据库
```

### 可调参数（位于 `SnoringDetector.kt`）

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `rmsThreshold` | `0.02` | RMS 能量阈值（0–1 归一化），调高可减少误报 |
| `lowBandRatio` | `0.35` | 低频带（100–500 Hz）能量最低占比 |
| `minConsecutiveHits` | `3` | 连续满足条件的帧数，调高可减少短促误报 |
| `sampleRate` | `16000` | 采样率（Hz） |
| `frameSize` | `1024` | 每帧采样数 |

---

## 项目结构

```
app/src/main/java/com/example/snoringdetection/
├── MainActivity.kt          # 主界面（Jetpack Compose）
├── audio/
│   └── AudioCapture.kt      # AudioRecord 封装，输出归一化浮点帧流
├── data/
│   ├── SnoringEvent.kt      # Room 实体
│   ├── SnoringEventDao.kt   # Room DAO
│   ├── SnoringDatabase.kt   # Room 数据库单例
│   └── SnoringRepository.kt # 数据仓库层
├── detection/
│   └── SnoringDetector.kt   # 检测算法（RMS + 频带能量）
├── service/
│   └── SnoringDetectionService.kt  # 前台服务
└── ui/
    ├── MainViewModel.kt     # ViewModel（服务绑定 + 状态代理）
    └── theme/
        └── Theme.kt         # Material3 主题
```

---

## 已知限制与后续路线图

### 已知限制

- **算法精度**：当前使用启发式规则，存在误报（将其他低频噪声识别为鼾声）和漏报（轻微鼾声能量不足）。
- **简化 DFT**：仅计算少量代表性频点，非完整 FFT，频率分辨率有限。
- **未做噪声自适应**：背景噪声基线未动态估算，建议在安静环境使用。
- **没有夜间模式调度**：需要用户手动开始/停止，未实现定时自动启动。

### 后续路线图

- [ ] 接入 TensorFlow Lite 或 ONNX Runtime，使用训练好的鼾声识别模型替换启发式算法
- [ ] 噪声基线动态估算（自适应阈值）
- [ ] 每晚报告：用图表展示整晚鼾声频率和时长分布
- [ ] 蓝牙音频支持
- [ ] 定时自动开始/停止检测（配合闹钟）
- [ ] 云端同步与数据导出（CSV/PDF）
- [ ] Android Widget 快捷启动

---

## 构建命令

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK（需要签名配置）
./gradlew assembleRelease

# 运行单元测试
./gradlew test

# 运行 Instrumented 测试（需要设备/模拟器）
./gradlew connectedAndroidTest
```
