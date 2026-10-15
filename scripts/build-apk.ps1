# 构建 Debug APK
# 用法：powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1

param(
    [switch]$Install,       # 构建后立即 adb install
    [switch]$Release        # 构建 Release（默认 Debug）
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

Write-Host "===== 构建 Android APK =====" -ForegroundColor Cyan
Write-Host "项目: $root"
Write-Host ""

# 0. 检查环境
$javaHome = $env:JAVA_HOME
if (-not $javaHome -or -not (Test-Path "$javaHome\bin\javac.exe")) {
    Write-Host "✗ 未设置 JAVA_HOME 或路径无效" -ForegroundColor Red
    Write-Host "  请先执行 scripts\check-env.ps1 确认环境" -ForegroundColor Yellow
    exit 1
}

$sdkHome = $env:ANDROID_HOME
if (-not $sdkHome -or -not (Test-Path "$sdkHome\platform-tools\adb.exe")) {
    Write-Host "✗ 未设置 ANDROID_HOME 或 SDK 未安装" -ForegroundColor Red
    exit 1
}

# 1. 写 local.properties（Gradle 用）
$localProps = Join-Path $root "local.properties"
"sdk.dir=$sdkHome`r`n" | Out-File -Encoding ASCII -FilePath $localProps
Write-Host "✓ 写入 local.properties (sdk.dir=$sdkHome)" -ForegroundColor Green

# 2. 检查 gradle-wrapper.jar（首次构建需要它）
$wrapperJar = Join-Path $root "gradle\wrapper\gradle-wrapper.jar"
if (-not (Test-Path $wrapperJar)) {
    Write-Host "⚠ 缺少 gradle-wrapper.jar" -ForegroundColor Yellow
    Write-Host "  方案 A：在 Android Studio 中打开项目让 AS 自动生成" -ForegroundColor Cyan
    Write-Host "  方案 B：执行 'gradle wrapper --gradle-version 8.10' (需要本机有 Gradle)" -ForegroundColor Cyan
    Write-Host "  方案 C：访问 https://github.com/gradle/gradle/releases 下载完整 Gradle 后运行 wrapper 任务" -ForegroundColor Cyan
    Write-Host ""
    $choice = Read-Host "是否仍尝试系统 gradle 构建？[y/N]"
    if ($choice -ne "y" -and $choice -ne "Y") {
        exit 1
    }
    $useSystemGradle = $true
} else {
    $useSystemGradle = $false
}

# 3. 执行构建
$task = if ($Release) { ":app:assembleRelease" } else { ":app:assembleDebug" }
Write-Host ""
Write-Host "执行: gradle $task" -ForegroundColor Cyan

if ($useSystemGradle) {
    & gradle $task --no-daemon
} else {
    .\gradlew.bat $task --no-daemon
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ 构建失败" -ForegroundColor Red
    exit 1
}

# 4. 查找 APK
$apkType = if ($Release) { "release" } else { "debug" }
$apkPath = Join-Path $root "app\build\outputs\apk\$apkType\app-$apkType.apk"
if (-not (Test-Path $apkPath)) {
    Write-Host "✗ 找不到 APK: $apkPath" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "✓ APK 已生成: $apkPath" -ForegroundColor Green
$size = (Get-Item $apkPath).Length / 1MB
Write-Host ("  大小: {0:N2} MB" -f $size) -ForegroundColor Green

# 5. 安装
if ($Install) {
    Write-Host ""
    Write-Host "尝试安装到设备..." -ForegroundColor Cyan
    $adb = "$sdkHome\platform-tools\adb.exe"
    $devices = & $adb devices | Where-Object { $_ -match "device$" }
    if ($devices.Count -eq 0) {
        Write-Host "✗ 未检测到设备，请连接手机并开启 USB 调试" -ForegroundColor Red
    } else {
        & $adb install -r $apkPath
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ 安装成功" -ForegroundColor Green
            Write-Host "  在手机上打开『离线翻译』即可" -ForegroundColor Cyan
        } else {
            Write-Host "✗ 安装失败" -ForegroundColor Red
        }
    }
}
