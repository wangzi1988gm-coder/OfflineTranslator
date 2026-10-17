# 构建与运行

> 目标：荣耀 500 Pro（MEP-AN00，Android 16 / MagicOS 10）。理论上对 Android 8.0+ 设备通用。

## 1. 环境

> 本仓库当前通过 GitHub Actions 使用 Gradle 8.10.2 构建；本地命令行构建需先安装 Gradle 8.10.2。

| 组件 | 版本 | 备注 |
| --- | --- | --- |
| JDK | 17 | Android Studio 自带 |
| Android Studio | 2024.x 或更新 | Kotlin 2.0+ |
| Gradle | 8.10+ | 跟随 AGP 8.7 |
| Android SDK | API 35（compileSdk） | minSdk = 26 |
| NDK | 26.x（可选） | sherpa-onnx / onnxruntime 自带 .so |

## 2. 第一次构建

```bash
git clone <repo>
cd OfflineTranslator
gradle :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 3. 在手机上安装

```bash
adb install -r app-debug.apk
```

或通过 Android Studio：Run → 选荣耀 500 Pro → 运行。

## 4. 关键配置

### 4.1 ABI 过滤

`app/build.gradle.kts` 已设置：
```kotlin
ndk {
    abiFilters += listOf("arm64-v8a", "armeabi-v7a")
}
```
荣耀 500 Pro 是 arm64-v8a，会命中。

### 4.2 不联网（强制）

`AndroidManifest.xml` 已**不声明** `android.permission.INTERNET`。
任何运行时网络调用都会被系统拒绝，确保离线。

### 4.3 大模型不压缩

在 `app/build.gradle.kts` 添加（如果内置大模型）：
```kotlin
android {
    androidResources {
        additionalParameters("--no-compress", "onnx", "spm", "bin")
    }
}
```

### 4.4 内存调优

荣耀 500 Pro 有 12 GB RAM，但单应用有上限（默认 ~512 MB）。
为支持多模型驻留，已在 Manifest 加 `android:largeHeap="true"`。

## 5. 模型导入

详见 `docs/MODELS.md`。

简版流程：
1. 启动 App → 「模型」页。
2. 「导入模型目录」→ 选下载好的根目录。
3. 「重新扫描」→ 列表显示已识别模型。
4. 进入「翻译」/「对话」/「图片」即可使用。

## 6. 调试技巧

### 6.1 logcat

```bash
adb logcat -s MarianMtEngine SherpaOnnxAsrEngine SherpaOnnxTtsEngine \
              MlKitOcrEngine ModelManager SystemTtsEngine
```

### 6.2 检查应用占用的内存

```bash
adb shell dumpsys meminfo com.example.offlinetranslator
```

### 6.3 检查模型目录

```bash
adb shell run-as com.example.offlinetranslator ls -la files/models/
```

## 7. 已知问题与排错

| 现象 | 可能原因 | 处理 |
| --- | --- | --- |
| 翻译显示"未找到离线模型" | 对应 (src, tgt) 的 NMT 模型未导入 | 到「模型」页导入 |
| 对话模式无 ASR | ASR 模型缺失 | 导入 sherpa-onnx ASR 模型 |
| 塔吉克语朗读错 | 缺少 tg TTS 模型；俄文兜底 | 在设置页选择"仅离线 VITS"或导入 VITS-ru |
| ML Kit 报错 | 设备 Play Services 缺失 | ML Kit Text Recognition 不依赖 Play Services |
| 中文 OCR 慢 | 大图片未压缩 | 在 `ImageTranslateViewModel` 中调小 maxDim |

## 8. 性能压测建议

在荣耀 500 Pro 上跑以下场景，确认无明显卡顿：

- 长按麦克风 10 秒，应能流畅看到电平。
- 100 字中文翻译成英文，端到端 < 1.5 s。
- 1000 万像素图片 OCR < 2 s。
- 连续 50 次翻译，内存不持续上涨（无泄漏）。

## 9. CI / 自动化

`build.gradle.kts` 未配置 CI。可加：
```yaml
# .github/workflows/build.yml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :app:assembleDebug
```
