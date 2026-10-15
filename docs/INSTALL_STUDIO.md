# Windows + Android Studio 一键安装（路径 A · 详细版）

> 这是一份为荣耀 500 Pro 用户准备的最详细步骤，跟着做完就能跑起来。

## 总时长预估
- 下载 Android Studio（≈ 1.5 GB）：20-60 分钟（取决于网速）
- 安装 + 首次 SDK 下载：20-30 分钟
- Gradle Sync + 依赖下载：5-10 分钟（首次）
- 编译 APK + 安装到手机：3-5 分钟

## 第 1 步：下载 Android Studio

1. 浏览器打开 https://developer.android.com/studio
2. 点击绿色按钮 **"Download Android Studio Koala Feature Drop (2024.1.2)"**
3. 在下载页面勾选 **"I have read and agree with the above terms and conditions"**
4. 点击 **"Download Android Studio ..."** 开始下载（Windows 版本约 1.5 GB）

> 国内加速：如果官网下载慢，可在腾讯软件中心 / 华为镜像站搜索 "Android Studio" 找带 Koala 字样的版本。

## 第 2 步：安装

1. 双击下载的 `.exe`（文件名形如 `android-studio-2024.1.2.xx-windows.exe`）
2. **Welcome**：点 Next
3. **Choose Components**：全选（Android Studio、Android SDK、Android Virtual Device、Performance (Intel HAXM)）
4. **Install Locations**：
   - Android Studio 安装路径：默认 `C:\Program Files\Android\Android Studio`（推荐）
   - Android SDK 安装路径：**强烈建议改到非 C 盘**，例如 `D:\Android\Sdk`（荣耀 500 Pro 用户数据量大，SDK 几个 GB 不能放 C 盘）
5. **Install**：等待 5-10 分钟
6. **Next → Finish**

## 第 3 步：首次启动配置

1. 启动 Android Studio（桌面图标）
2. **Import Android Studio Settings**：选 **Do not import settings** → OK
3. **Data Sharing**：随便选
4. **Welcome → Send usage statistics to Google**：随便选
5. **Android Studio Setup Wizard**：
   - Install Type：**Standard**（推荐）
   - Select UI Theme：**Light** 或 **Darcula**（随便）
   - Verify Settings：显示 SDK 安装位置，确认是刚才设置的
   - License Agreement：选 **android-sdk-license** 与 **android-sdk-preview-license**，点 Accept
   - **Finish**（开始下载 SDK 平台 35、Build-Tools 35，约 500 MB，3-5 分钟）

## 第 4 步：打开项目

1. **Welcome 界面** → **Open**（不要选 New Project）
2. 浏览到 `D:\fanyi\OfflineTranslatorAndroid_source\`（如果项目不在 D 盘，对应调整）
3. 选中项目根文件夹（包含 `settings.gradle.kts` 的那个）→ OK
4. Android Studio 开始 **Gradle Sync**：
   - 右下角进度条会显示 "Gradle: Configure..."
   - 状态栏底部："Downloading Gradle 8.10.2"（约 150 MB）
   - 接下来下载所有 Maven 依赖（Compose、ONNX Runtime、sherpa-onnx、ML Kit 等），约 500 MB
   - **这一步最容易卡住，详见下文"网络慢"小节**

## 第 5 步：连接荣耀 500 Pro

### 5.1 打开开发者选项 + USB 调试

在荣耀 500 Pro 上：

1. **设置** → **关于手机** → 连续点击 **版本号** 7 次（会提示"已进入开发者模式"）
2. 返回 **设置** → **系统和更新** → **开发人员选项**（或在搜索框输入"开发人员"）
3. 进入 **开发人员选项**，打开：
   - **USB 调试**（必开）
   - **USB 安装** / **USB 调试（安全设置）**（部分机型需要）
   - **"仅充电"模式下允许 ADB 调试**（可选，方便锁屏调试）
4. 用 **原装数据线**（或确认支持数据传输的线）连接电脑

### 5.2 信任电脑

手机会弹出：
> **"允许 USB 调试吗？"**
> - RSA 密钥指纹：...
> - 勾选"始终允许使用此计算机进行调试"
> - 点 **确定**

### 5.3 在 Android Studio 确认连接

Android Studio 右上角设备选择框应显示你的设备：
```
HUAWEI MEP-AN00
```
如果没有：

1. 检查 Windows 是否识别了设备：右下角通知中心看 USB 设备提示
2. 装 HiSuite 驱动：https://consumer.huawei.com/cn/support/hisuite/（华为手机管家自带的 USB 驱动）
3. 重启 Android Studio；或 File → Sync Project with Gradle Files

## 第 6 步：运行

1. 确认顶部设备选择框选中了你的荣耀 500 Pro
2. 点绿色 ▶ **Run 'app'** 按钮（或 Shift + F10）
3. Android Studio 自动完成：
   - 编译 Debug APK（首次 3-5 分钟）
   - 推送 APK 到手机
   - 安装
   - 启动主 Activity
4. 手机上出现 **"离线翻译"** 应用图标

## 第 7 步：首次使用

1. 打开 **离线翻译** App
2. 看到主界面：
   - 顶部方向选择（默认中→英）
   - 中间文本输入框
   - 底部 5 个 tab（翻译 / 对话 / 图片 / 模型 / 设置）
3. 进入 **模型** tab：
   - 应该看到 4 个语种 × 3 类（翻译/ASR/TTS）共 14 个槽位
   - 大部分显示 "未安装"
4. 进入 **设置** tab：调整主题、朗读引擎等
5. 切回 **翻译** tab：输入文字测试 → 会显示"未找到离线模型"友好提示（因为还没导入模型）

## 常见问题

### Q1: Gradle Sync 一直转圈 / 失败
**A**: 国内访问 Maven 慢。改用阿里云镜像：
1. 在 `~/.gradle/init.gradle.kts` 创建文件（不存在就新建）：

```kotlin
allprojects {
    repositories {
        mavenLocal()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
    }
}

settingsEvaluated {
    pluginManagement {
        repositories {
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            gradlePluginPortal()
            google()
            mavenCentral()
        }
    }
}
```

2. 重启 Android Studio → File → Sync

### Q2: 编译报错 "Failed to resolve: com.k2fsa.sherpaonnx:sherpa-onnx:1.10.20"
**A**: 网络问题。先确保 Maven 镜像生效；如果仍然失败，把 `app/build.gradle.kts` 里 sherpa-onnx 版本改成 `1.10.15`（更老的版本可能 mirror 已有）。

### Q3: 安装到手机报 "INSTALL_FAILED_USER_RESTRICTED"
**A**: 手机关闭了"开发者选项 → USB 安装限制"。或者手机管家 / 病毒软件拦截，关闭即可。

### Q4: 手机识别不到
**A**:
1. 换数据线（**必须支持数据**，纯充电线不行）
2. 装 HiSuite（自带驱动）
3. 重启手机 + 重启 Android Studio
4. 检查：Windows 设备管理器里有没有 Android Device / ADB Interface

### Q5: 想让别人远程给模型
**A**: 模型文件很大（2-3 GB），建议局域网共享文件夹 / 微信文件传输助手 / 百度网盘。

## 下一步

- 详细看：[docs/MODELS.md](MODELS.md) 下载模型
- 详细看：[docs/CONVERSATION.md](CONVERSATION.md) 了解语音对讲
- 详细看：[docs/IMAGE_TRANSLATION.md](IMAGE_TRANSLATION.md) 了解图片翻译

## 不需要 Android Studio 的备选方案

如果你不想装 Android Studio（占空间大），还有两条路：

1. **GitHub Actions 远程构建**：把代码 push 到 GitHub → Actions 自动构建 → 下载 APK。详见 INSTALL.md 路径 B
2. **纯命令行**：装 JDK 17 + Android SDK cmdline-tools，跑 `scripts\build-apk.ps1`。详见 INSTALL.md 路径 C
