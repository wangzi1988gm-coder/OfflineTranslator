package com.example.offlinetranslator.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 模型清单（manifest.json）。
 *
 * 存放在应用外部存储根下的 `manifest.json`，描述已识别的模型包。
 * ModelManager 启动时读取此清单重建 ModelPack 列表。
 *
 * 格式示例：
 * {
 *   "version": 1,
 *   "packs": [
 *     {
 *       "slotKey": "zh-en",
 *       "category": "TRANSLATOR",
 *       "engineImpl": "MARIAN_MT",
 *       "source": "USER_IMPORTED",
 *       "rootDir": "translator/zh-en",
 *       "primaryFile": "model.onnx",
 *       "sizeBytes": 123456789,
 *       "version": "1",
 *       "checksumSha256": "sha256:...",
 *       "notes": ""
 *     }
 *   ]
 * }
 */
@Serializable
data class ManifestFile(
    val version: Int = 1,
    val packs: List<ModelPack> = emptyList(),
)

class ModelManifest(private val file: File) {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): List<ModelPack> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(ManifestFile.serializer(), file.readText(Charsets.UTF_8)).packs
        }.getOrDefault(emptyList())
    }

    fun save(packs: List<ModelPack>) {
        file.parentFile?.mkdirs()
        val data = ManifestFile(version = 1, packs = packs)
        file.writeText(json.encodeToString(ManifestFile.serializer(), data), Charsets.UTF_8)
    }
}
