# 检查 Android 构建环境是否就绪
# 用法：powershell -ExecutionPolicy Bypass -File scripts\check-env.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

Write-Host "===== Android 构建环境检测 =====" -ForegroundColor Cyan
Write-Host "项目根目录: $root"
Write-Host ""

# 1. Java
Write-Host "[1/5] JDK 检测..." -ForegroundColor Yellow
$javaOk = $false
$jdkPaths = @(
    "$env:JAVA_HOME",
    "C:\Program Files\Java\jdk-17",
    "C:\Program Files\Eclipse Adoptium\jdk-17",
    "C:\Program Files\Microsoft\jdk-17",
    "C:\Program Files\Android Studio\jbr",
)
foreach ($p in $jdkPaths) {
    if ($p -and (Test-Path "$p\bin\javac.exe")) {
        Write-Host "  ✓ 找到 JDK: $p" -ForegroundColor Green
        $env:JAVA_HOME = $p
        $javaOk = $true
        break
    }
}
if (-not $javaOk) {
    Write-Host "  ✗ 未找到 JDK 17" -ForegroundColor Red
    Write-Host "    建议安装: https://adoptium.net/temurin/releases/?version=17"
}
Write-Host ""

# 2. Android SDK
Write-Host "[2/5] Android SDK 检测..." -ForegroundColor Yellow
$sdkPaths = @(
    "$env:ANDROID_HOME",
    "$env:ANDROID_SDK_ROOT",
    "$env:LOCALAPPDATA\Android\Sdk",
    "$env:USERPROFILE\AppData\Local\Android\Sdk",
    "D:\Android\Sdk",
    "C:\Android\Sdk",
)
$sdkOk = $false
foreach ($p in $sdkPaths) {
    if ($p -and (Test-Path "$p\platform-tools\adb.exe")) {
        Write-Host "  ✓ 找到 SDK: $p" -ForegroundColor Green
        $env:ANDROID_HOME = $p
        $sdkOk = $true
        break
    }
}
if (-not $sdkOk) {
    Write-Host "  ✗ 未找到 Android SDK" -ForegroundColor Red
    Write-Host "    推荐安装 Android Studio（自带 SDK）：https://developer.android.com/studio"
}
Write-Host ""

# 3. Gradle
Write-Host "[3/5] Gradle 检测..." -ForegroundColor Yellow
$gradleOk = $false
try {
    $v = & gradle --version 2>&1 | Select-String -Pattern "Gradle \d"
    if ($v) {
        Write-Host "  ✓ 系统 Gradle: $v" -ForegroundColor Green
        $gradleOk = $true
    }
} catch {}
if (-not $gradleOk -and (Test-Path "$root\gradlew.bat")) {
    Write-Host "  ⚠ 系统未装 Gradle，但项目自带 gradle wrapper" -ForegroundColor Yellow
    Write-Host "    首次运行 gradlew.bat 会自动下载 Gradle 8.10"
}
Write-Host ""

# 4. ADB / 设备连接
Write-Host "[4/5] ADB 与手机连接检测..." -ForegroundColor Yellow
$adbOk = $false
$adbPath = "$env:ANDROID_HOME\platform-tools\adb.exe"
if (Test-Path $adbPath) {
    try {
        $devices = & $adbPath devices 2>&1
        if ($devices -match "device$") {
            Write-Host "  ✓ 找到已连接设备：" -ForegroundColor Green
            $devices | Where-Object { $_ -match "device$" } | ForEach-Object { Write-Host "    $_" }
            $adbOk = $true
        } else {
            Write-Host "  ⚠ ADB 可用，但未检测到设备" -ForegroundColor Yellow
            Write-Host "    请打开手机开发者选项 + USB 调试，再用数据线连接"
        }
    } catch {
        Write-Host "  ✗ ADB 调用失败" -ForegroundColor Red
    }
} else {
    Write-Host "  ✗ 找不到 ADB" -ForegroundColor Red
}
Write-Host ""

# 5. 总结
Write-Host "[5/5] 总结" -ForegroundColor Yellow
if ($javaOk -and $sdkOk) {
    Write-Host "  ✓ 环境就绪，可以构建 APK！" -ForegroundColor Green
    Write-Host ""
    Write-Host "  下一步：执行 scripts\\build-apk.ps1" -ForegroundColor Cyan
} else {
    Write-Host "  ✗ 环境不完整，请先补齐缺失项" -ForegroundColor Red
    Write-Host ""
    Write-Host "  最快方案：安装 Android Studio（自带 JDK + Android SDK + Gradle）" -ForegroundColor Cyan
    Write-Host "  下载: https://developer.android.com/studio" -ForegroundColor Cyan
}
Write-Host ""
