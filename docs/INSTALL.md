# 如何把源码变成可安装的 APK

> 项目交付物是 **完整 Android Studio 工程**（含 70 个源文件 + 文档），不直接包含 APK。
> 本文给出 **3 条安装路径**，按推荐顺序排列——选最适合你环境的一条即可。

## 路径 A：用 Android Studio 一键运行（最简单）

适合：你有自己的 Windows / Mac / Linux 开发机，愿意装一个 Android Studio（≈ 1.5 GB）。

### 步骤

1. **下载 Android Studio**
   - 网址：https://developer.android.com/studio
   - 选 "Android Studio Koala Feature Drop (2024.1.2)" 或更新稳定版
   - 安装时勾选：Android SDK、Android Virtual Device、Performance (Intel HAXM)

2. **首次启动**
   - Studio 会自动下载 JDK 17、Android SDK Platform 35、Android Build-Tools 35
   - 接受所有 License

3. **打开项目**
   - File → Open → 选 `D:\fanyi\OfflineTranslatorAndroid_source\`（解压后的文件夹）
   - 等待 Gradle Sync 完成（首次会下载 Gradle 8.10 与所有依赖，约 5-10 分钟）

4. **连接荣耀 500 Pro**
   - 手机 → 设置 → 关于手机 → 连点 7 次"版本号"打开开发者选项
   - 开发者选项 → 打开 USB 调试
   - 用数据线连接电脑
   - 手机弹出"允许 USB 调试"→ 确定
   - Android Studio 顶部工具栏会显示设备名

5. **运行**
   - 点工具栏绿色 ▶ Run 'app'
   - 第一次会自动安装 APK 到手机并启动

### 遇到问题？

| 现象 | 处理 |
| --- | --- |
| Gradle Sync 慢 | 切到国内镜像（见 BUILD.md） |
| 设备未识别 | 换数据线（必须支持数据，不是只充电）；安装 HiSuite 驱动 |
| INSTALL_FAILED_USER_RESTRICTED | 手机关闭"开发者选项 → USB 安装限制" |
| build 报错 sherpa-onnx | 网络不稳多试几次，或换 jitpack 镜像 |

## 路径 B：用 GitHub Actions 远程构建（无需本地环境）

适合：你不想装 Android Studio，但有 GitHub 账号；或希望 CI 自动出 APK。

### 步骤

1. 把项目推到 GitHub：
   ```bash
   cd OfflineTranslatorAndroid_source
   git init
   git add .
   git commit -m "init"
   git branch -M main
   git remote add origin git@github.com:<你的用户名>/offline-translator.git
   git push -u origin main
   ```

2. 打开 GitHub → 你的仓库 → Actions → Build Android APK → Run workflow

3. 等 5-10 分钟，下载 `app-debug` 产物里的 `app-debug.apk`

4. 把 APK 拷贝到手机任意目录，点击安装（首次会提示"未知来源"，允许即可）

### 注意

- GitHub Actions 每月免费额度 2000 分钟，足够用。
- APK 是 Debug 签名；要发给别人用需要打 Release 签名（自行配置 keystore）。

## 路径 C：纯命令行构建（轻量方案）

适合：你不想装 Android Studio，但愿意装 JDK 和 Android SDK 命令行工具。

### 步骤

1. **装 JDK 17**
   - Adoptium Temurin 17：https://adoptium.net/temurin/releases/?version=17
   - 设置环境变量 `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.x.x`

2. **装 Android SDK 命令行工具**
   - 下载：https://developer.android.com/studio#command-line-tools-only
   - 解压到 `D:\Android\cmdline-tools`
   - 设置 `ANDROID_HOME=D:\Android`
   - 把 `%ANDROID_HOME%\cmdline-tools\bin` 和 `%ANDROID_HOME%\platform-tools` 加到 PATH

3. **装 SDK 组件**
   ```powershell
   sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
   ```

4. **装 Gradle（任选其一）**
   - 系统装 Gradle 8.10：https://gradle.org/releases/
   - 或运行项目根目录的 `gradlew.bat`（首次会自动下载）

5. **运行本项目提供的构建脚本**
   ```powershell
   cd D:\fanyi\OfflineTranslatorAndroid_source
   powershell -ExecutionPolicy Bypass -File scripts\check-env.ps1     # 先检查
   powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1 -Install
   ```

### ⚠️ gradle-wrapper.jar 缺失

由于我无法在源码里直接生成二进制 jar，路径 C 首次运行需要：

**方案 1（推荐）**：把项目先在 Android Studio 里打开一次，让 AS 自动生成 `gradle/wrapper/gradle-wrapper.jar`，然后再用命令行。

**方案 2**：本机装 Gradle 后执行：
```bash
gradle wrapper --gradle-version 8.10.2
```
会自动生成 wrapper.jar。

**方案 3**：直接用系统 gradle 跳过 wrapper：
```powershell
gradle :app:assembleDebug
```

## 路径 D：让别人帮构建

如果你完全不想折腾：

1. 把 `OfflineTranslatorAndroid_source_v1.0.zip` 发给懂 Android 的朋友
2. 让他们按路径 A 在 Android Studio 里打开 → Build APK → 把 APK 发回来
3. 你拿到 APK 后通过微信 / 邮件传到手机，点击安装

## 模型导入（无论走哪条路径都要做）

APK 安装后只是空壳。还需要：

1. 下载模型（详见 `docs/MODELS.md`）
   - 翻译模型（OPUS-MT）：从 HuggingFace Helsinki-NLP 下载并转 ONNX
   - ASR / TTS 模型：从 sherpa-onnx GitHub Release 下载

2. 启动 App → 「模型」页 → 「导入模型目录」

3. 选择模型根目录即可（App 自动识别并拷贝到 `/Android/data/com.example.offlinetranslator/files/models/`）

## 验证是否成功

- 打开「离线翻译」App
- 主界面底部导航出现 5 个 tab（翻译 / 对话 / 图片 / 模型 / 设置）
- 在「模型」页应该看到已识别的模型项
- 在「翻译」页输入"你好"，选 zh→en，点翻译 → 显示"Hello"或级联提示
- 第一次按"听写"或对话模式按钮会弹出麦克风权限请求

如果这些都正常，恭喜你已经在荣耀 500 Pro 上跑起来了 🎉。
