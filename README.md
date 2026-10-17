# 离线通译 1.0.0-dev（Honor 500 Pro 专用）

> 一款完全离线的中 ⇄ 英、中 ⇄ 俄、中 ⇄ 塔吉克翻译应用，覆盖文本翻译、语音对讲、图片翻译。
> 目标设备：荣耀 500 Pro（MEP-AN00，Android 16 / MagicOS 10，12GB RAM + 512GB ROM）。

## ⚡ 看到 APK 之前你需要知道的事

**项目交付物是完整源码工程，不是可直接安装的 APK。** 本仓库已配置 GitHub Actions 自动构建。每次推送代码后会尝试编译 Debug APK；构建成功后，在仓库的 Actions → Build Android APK → Artifacts 下载。当前源码仍需通过 CI 实际编译验证。

**推荐路径：在你自己电脑上装 Android Studio 一键运行** 📖 **[完整 Studio 安装步骤 → docs/INSTALL_STUDIO.md](docs/INSTALL_STUDIO.md)**

如果你不想装 Android Studio（占空间大），还有备选：
- GitHub Actions 远程构建（无需本地工具）
- 纯命令行构建（轻量）
- 让懂 Android 的朋友帮你构建

📖 详细对比见 **[docs/INSTALL.md](docs/INSTALL.md)**。

## 1. 功能概览

| 模块 | 能力 | 离线实现 |
| --- | --- | --- |
| 文本翻译 | 中 ⇄ 英、中 ⇄ 俄、中 ⇄ 塔吉克（共 6 个方向） | ONNX Runtime + OPUS-MT（MarianMT） |
| 语音对讲 | 双方轮流说话，自动识别 → 翻译 → 朗读 | sherpa-onnx ASR + NMT + Android TTS / sherpa-onnx VITS |
| 图片翻译 | 相机 / 相册 / 路牌 / 菜单 / 公文，OCR + 行级翻译 | Google ML Kit Text Recognition（本地） |
| 朗读 | 系统 TTS + 离线 VITS 兜底 | Android 系统 + sherpa-onnx VITS |
| 设置 | 主题、引擎偏好、VAD、双语图 | SharedPreferences |

> **塔吉克语方案**：因塔吉克语 ASR/TTS 开源模型稀缺，本工程采用：
> - ASR：复用俄文 ASR（西里尔字母互通）；
> - TTS：用俄文 VITS 朗读塔吉克文（发音近俄语语调）；
> - 翻译：通过 `zh ↔ ru` + `ru ↔ tg` 级联得到 zh ⇄ tg / en ⇄ tg。

## 2. 技术栈

- Kotlin 2.0 + Jetpack Compose（Material3）
- AndroidX 13+（Navigation、DataStore、Room、CameraX）
- ONNX Runtime Mobile 1.19.x（NMT 推理）
- sherpa-onnx 1.13.8（离线 ASR + TTS）
- Google ML Kit Text Recognition（本地 OCR）

## 3. 目录结构

```
OfflineTranslatorAndroid_source/
├── README.md                       本文件
├── docs/                           设计文档
│   ├── ARCHITECTURE.md
│   ├── MODELS.md
│   ├── BUILD.md
│   ├── CONVERSATION.md
│   └── IMAGE_TRANSLATION.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/
        │   └── models/manifest.json
        ├── java/com/example/offlinetranslator/
        │   ├── App.kt
        │   ├── MainActivity.kt
        │   ├── di/AppContainer.kt
        │   ├── data/
        │   │   ├── Language.kt
        │   │   ├── ModelPack.kt
        │   │   ├── ModelManifest.kt
        │   │   ├── Settings.kt
        │   │   ├── SettingsRepository.kt
        │   │   └── AppDatabase.kt
        │   ├── engine/
        │   │   ├── translator/
        │   │   │   ├── TranslationEngine.kt
        │   │   │   ├── MarianMtEngine.kt
        │   │   │   ├── CascadeTranslator.kt
        │   │   │   └── TranslatorRouter.kt
        │   │   ├── asr/
        │   │   │   ├── AsrEngine.kt
        │   │   │   ├── SherpaOnnxAsrEngine.kt
        │   │   │   ├── AsrRouter.kt
        │   │   │   └── AudioRecorder.kt
        │   │   ├── ocr/
        │   │   │   ├── OcrEngine.kt
        │   │   │   └── MlKitOcrEngine.kt
        │   │   └── tts/
        │   │       ├── TtsEngine.kt
        │   │       ├── SystemTtsEngine.kt
        │   │       ├── SherpaOnnxTtsEngine.kt
        │   │       └── TtsRouter.kt
        │   ├── model/
        │   │   ├── ModelStorage.kt
        │   │   └── ModelManager.kt
        │   ├── ui/
        │   │   ├── theme/         Color.kt, Theme.kt, Type.kt
        │   │   ├── component/     LanguagePicker, AudioWaveform, StatusChip
        │   │   ├── nav/AppNav.kt
        │   │   └── screen/
        │   │       ├── translate/     文本翻译
        │   │       ├── conversation/  语音对讲
        │   │       ├── image/         图片翻译
        │   │       ├── models/        模型管理
        │   │       └── settings/      设置
        │   └── util/
        │       ├── Permission.kt
        │       ├── LocaleHelper.kt
        │       └── Logger.kt
        └── res/                  strings, colors, themes, icons, xml
```

## 4. 快速开始

### 4.1 在 Android Studio 中打开

1. Android Studio → Open → 选择 `OfflineTranslatorAndroid_source/` 文件夹。
2. 等待 Gradle Sync。
3. 连接荣耀 500 Pro → Run。

### 4.2 命令行构建

```bash
cd OfflineTranslatorAndroid_source
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4.3 导入模型

1. 下载模型（详见 `docs/MODELS.md`）。
2. 启动 App → 「模型」页 → 「导入模型目录」。
3. 选择模型根目录即可。

## 5. 设计要点

- **真正离线**：Manifest 不声明 INTERNET 权限；所有引擎本地推理。
- **可降级**：每个引擎有兜底实现（系统 TTS / 词典级联 / 友好提示）。
- **模块化**：所有引擎实现接口，便于替换。
- **大内存友好**：Manifest `largeHeap=true`；按需懒加载模型。
- **塔吉克语桥接**：通过俄语做级联（NMT 与 ASR/TTS 全部利用）。

## 6. 文档

- [INSTALL_STUDIO.md](docs/INSTALL_STUDIO.md) — **🟢 Windows + Android Studio 一键安装（推荐）**
- [INSTALL.md](docs/INSTALL.md) — 4 种安装路径对比
- [ARCHITECTURE.md](docs/ARCHITECTURE.md) — 架构与模块
- [MODELS.md](docs/MODELS.md) — 模型获取、转换、导入
- [BUILD.md](docs/BUILD.md) — 构建、运行、调试
- [CONVERSATION.md](docs/CONVERSATION.md) — 语音对讲模式
- [IMAGE_TRANSLATION.md](docs/IMAGE_TRANSLATION.md) — 图片翻译模式

## 7. 致谢

- [HuggingFace Helsinki-NLP/OPUS-MT](https://huggingface.co/Helsinki-NLP) — NMT 模型（CC BY）
- [k2fsa/sherpa-onnx](https://github.com/k2fsa/sherpa-onnx) — 离线 ASR / TTS
- [Microsoft ONNX Runtime](https://onnxruntime.ai/) — 推理运行时
- [Google ML Kit](https://developers.google.com/ml-kit) — 本地 OCR
- 塔吉克语相关模型与词典
