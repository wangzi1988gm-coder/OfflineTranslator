package com.example.offlinetranslator.model

import android.content.Context
import java.io.File

/**
 * 模型存储位置策略。
 *
 *  用户无需关心具体路径。所有引擎通过 ModelStorage 拿到模型目录：
 *  - 默认 `/Android/data/<package>/files/models/` —— 卸载 App 时随之清除，无须额外权限。
 *  - 也可以放 assets 内（只读），但大模型放 assets 会显著增大 APK。
 *
 *  注意：APK 本身已经在 build.gradle.kts 中设置了 `android:hasAssets`，
 *  这里只读取已存在的 assets 目录用于"内置模型占位"。
 */
class ModelStorage(context: Context) {

    val externalRoot: File by lazy {
        // /Android/data/com.example.offlinetranslator[.debug]/files
        context.getExternalFilesDir(null) ?: context.filesDir
    }

    val modelsRoot: File = File(externalRoot, "models")
    val manifestFile: File = File(modelsRoot, "manifest.json")

    /** assets 内置模型的访问根。读取时流式复制。 */
    fun assetsRoot(): String = "models"

    fun translatorDir(slotKey: String): File = File(modelsRoot, "translator/$slotKey").apply { mkdirs() }
    fun asrDir(langKey: String): File = File(modelsRoot, "asr/$langKey").apply { mkdirs() }
    fun ttsDir(langKey: String): File = File(modelsRoot, "tts/$langKey").apply { mkdirs() }

    fun ensureModelsRoot(): File {
        modelsRoot.mkdirs()
        return modelsRoot
    }

    /** 用户可读路径，用于设置页提示 */
    fun userHintPath(): String = externalRoot.absolutePath + "/models/"

    /** 列出某分类下的所有目录（用于扫描已导入模型）。 */
    fun listAllUnderModels(): List<File> =
        if (modelsRoot.exists()) modelsRoot.walkTopDown().filter { it.isDirectory }.toList()
        else emptyList()
}
