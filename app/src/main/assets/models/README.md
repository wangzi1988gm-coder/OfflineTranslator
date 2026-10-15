# 内置模型占位目录

为了让 APK 结构合法、不破坏 ModelManager 的 assets 扫描逻辑，这里放置了 manifest.json。
真正的 ONNX 模型文件请通过以下两种方式之一注入：

1) **APK 内置**：在 `assets/models/<category>/<key>/` 下放置真实模型文件。
   例如：`assets/models/translator/zh-en/encoder.onnx`。

2) **运行时导入**：用户从模型管理页选择本地目录（SAF），App 会拷贝到
   `/Android/data/com.example.offlinetranslator/files/models/<category>/<key>/`。

如果只通过 assets 内置模型，需要修改 `app/build.gradle.kts` 添加：
```kotlin
android {
    androidResources {
        additionalParameters("--no-compress", "onnx", "spm", "bin")
    }
}
```
避免 ONNX/SPM 文件被压缩影响运行时 mmap。

详见 `docs/MODELS.md`。
