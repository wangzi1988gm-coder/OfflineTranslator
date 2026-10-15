package com.example.offlinetranslator.model

import android.content.Context
import com.example.offlinetranslator.data.ModelCategory
import com.example.offlinetranslator.data.ModelManifest
import com.example.offlinetranslator.data.ModelPack
import com.example.offlinetranslator.data.ModelSlots
import com.example.offlinetranslator.data.ModelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 模型管理器。
 *
 * 职责：
 *  1. 从 manifest 读取已安装模型
 *  2. 在 assets 中查找内置占位模型
 *  3. 暴露每个槽位的"已就绪"状态
 *  4. 提供删除、导入（用户导入通过 SAF 在 UI 层调用）
 *
 * 注意：本管理器只负责"已识别的模型"，不负责把它们加载进内存。
 * 加载到内存由各个 engine 自己负责（懒加载）。
 */
class ModelManager(
    private val context: Context,
    private val storage: ModelStorage,
) {

    private val manifest = ModelManifest(storage.manifestFile)

    private val _packs = MutableStateFlow<List<ModelPack>>(emptyList())
    val packs: StateFlow<List<ModelPack>> = _packs.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** 启动时调用：读取 manifest + 扫描目录 + 检查 assets */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        _loading.value = true
        try {
            val fromManifest = manifest.load()

            // 扫描目录重建
            val scanned = scanFilesystem()

            // 合并（manifest 优先），同时去重（同 slotKey 保留 sources 最多的）
            val merged = mergeUnique(fromManifest + scanned)

            // 检查 assets 中是否有内置模型
            val builtin = scanAssets()
            val withAssets = mergeUnique(merged + builtin)

            _packs.value = withAssets.sortedBy { it.category.name + ":" + it.slotKey }
            manifest.save(_packs.value)
        } finally {
            _loading.value = false
        }
    }

    /** 槽位是否已就绪（有 pack） */
    fun isSlotReady(category: ModelCategory, slotKey: String): Boolean =
        _packs.value.any { it.category == category && it.slotKey == slotKey }

    fun packFor(category: ModelCategory, slotKey: String): ModelPack? =
        _packs.value.firstOrNull { it.category == category && it.slotKey == slotKey }

    fun packsByCategory(category: ModelCategory): List<ModelPack> =
        _packs.value.filter { it.category == category }

    /** 删除某个 pack 对应的文件目录 */
    suspend fun deletePack(pack: ModelPack) = withContext(Dispatchers.IO) {
        val dir = File(storage.modelsRoot, pack.rootDir).let {
            // rootDir 可能包含子目录，尝试删除其父目录
            if (it.exists()) it else File(storage.modelsRoot, pack.rootDir.substringBeforeLast('/'))
        }
        dir.deleteRecursively()
        _packs.value = _packs.value.filterNot { it.slotKey == pack.slotKey && it.engineImpl == pack.engineImpl }
        manifest.save(_packs.value)
    }

    /** 用户导入的模型根目录拷贝（通过 SAF 选择的目录） */
    suspend fun importDirectory(srcDir: File): List<ModelPack> = withContext(Dispatchers.IO) {
        require(srcDir.isDirectory) { "srcDir 必须为目录" }
        val detected = detectPacksInDir(srcDir)
        if (detected.isEmpty()) return@withContext emptyList()

        // 拷贝到 modelsRoot/<rootDir>
        val newPacks = detected.mapNotNull { detected ->
            val dest = File(storage.modelsRoot, detected.rootDir)
            dest.mkdirs()
            srcDir.copyRecursively(dest, overwrite = true)
            detected.copy(source = ModelSource.USER_IMPORTED).also {
                // 重算大小
                val size = dest.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                _packs.value = _packs.value + it.copy(sizeBytes = size)
            }
        }
        manifest.save(_packs.value)
        newPacks
    }

    // ---------- 私有：扫描 ----------

    private fun scanFilesystem(): List<ModelPack> {
        val out = mutableListOf<ModelPack>()
        if (!storage.modelsRoot.exists()) return out

        // translator/<key>/*.onnx
        File(storage.modelsRoot, "translator").listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            val onnx = dir.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".onnx") }
            if (onnx != null) {
                val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                out += ModelPack(
                    slotKey = dir.name,
                    category = ModelCategory.TRANSLATOR,
                    engineImpl = detectTranslatorImpl(dir),
                    source = ModelSource.USER_IMPORTED,
                    rootDir = "translator/${dir.name}",
                    primaryFile = onnx.name,
                    sizeBytes = size,
                )
            }
        }

        // asr/<lang>/* sherpa 模型
        File(storage.modelsRoot, "asr").listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            val tokens = dir.walkTopDown().firstOrNull {
                it.isFile && (it.name.endsWith(".onnx") || it.name == "tokens.txt")
            }
            if (tokens != null) {
                val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                out += ModelPack(
                    slotKey = "asr-${dir.name}",
                    category = ModelCategory.ASR,
                    engineImpl = "SHERPA_ONNX",
                    source = ModelSource.USER_IMPORTED,
                    rootDir = "asr/${dir.name}",
                    primaryFile = tokens.name,
                    sizeBytes = size,
                )
            }
        }

        // tts/<lang>/* VITS
        File(storage.modelsRoot, "tts").listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            val modelFile = dir.walkTopDown().firstOrNull {
                it.isFile && (it.name.endsWith(".onnx") || it.name.endsWith(".bin"))
            }
            if (modelFile != null) {
                val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                out += ModelPack(
                    slotKey = "tts-${dir.name}",
                    category = ModelCategory.TTS,
                    engineImpl = "SHERPA_ONNX_VITS",
                    source = ModelSource.USER_IMPORTED,
                    rootDir = "tts/${dir.name}",
                    primaryFile = modelFile.name,
                    sizeBytes = size,
                )
            }
        }

        return out
    }

    private fun scanAssets(): List<ModelPack> {
        val out = mutableListOf<ModelPack>()
        runCatching {
            val root = "models"
            context.assets.list(root)?.forEach { category ->
                val catPath = "$root/$category"
                context.assets.list(catPath)?.forEach { slotDir ->
                    val slotPath = "$catPath/$slotDir"
                    val files = context.assets.list(slotPath).orEmpty()
                    if (files.isEmpty()) return@forEach
                    val primary = files.firstOrNull { it.endsWith(".onnx") || it.endsWith(".bin") } ?: files.first()
                    out += ModelPack(
                        slotKey = if (category == "translator") slotDir else "$category-$slotDir",
                        category = ModelCategory.valueOf(category.uppercase()),
                        engineImpl = when (category) {
                            "translator" -> detectTranslatorImplFromName(slotDir)
                            "asr" -> "SHERPA_ONNX"
                            "tts" -> "SHERPA_ONNX_VITS"
                            else -> "AUTO"
                        },
                        source = ModelSource.ASSETS_BUILTIN,
                        rootDir = "$category/$slotDir",
                        primaryFile = primary,
                        sizeBytes = -1, // assets 中无法直接获取大小
                        notes = "内置模型（仅占位）",
                    )
                }
            }
        }
        return out
    }

    private fun detectTranslatorImpl(dir: File): String {
        val name = dir.name.lowercase()
        return when {
            name.startsWith("nllb") -> "NLLB_200"
            else -> "MARIAN_MT"
        }
    }

    private fun detectTranslatorImplFromName(slot: String): String =
        if (slot.lowercase().startsWith("nllb")) "NLLB_200" else "MARIAN_MT"

    private fun mergeUnique(packs: List<ModelPack>): List<ModelPack> {
        // 同一个 slotKey + engineImpl 只保留一份；ASSETS 优先级别最低
        val priority = mapOf(
            ModelSource.ASSETS_BUILTIN to 0,
            ModelSource.USER_IMPORTED to 1,
            ModelSource.SYSTEM to 2,
        )
        return packs
            .groupBy { it.slotKey + "@" + it.engineImpl }
            .map { (_, list) -> list.maxBy { priority[it.source] ?: 0 } }
    }

    private fun detectPacksInDir(dir: File): List<ModelPack> {
        val out = mutableListOf<ModelPack>()
        val onnx = dir.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".onnx") }
        if (onnx != null) {
            val rel = dir.name
            out += ModelPack(
                slotKey = rel,
                category = ModelCategory.TRANSLATOR,
                engineImpl = detectTranslatorImpl(dir),
                source = ModelSource.USER_IMPORTED,
                rootDir = "translator/$rel",
                primaryFile = onnx.name,
                sizeBytes = -1,
            )
        }
        // 检查 ASR 特征文件
        val tokens = dir.walkTopDown().firstOrNull { it.isFile && it.name == "tokens.txt" }
        if (tokens != null) {
            out += ModelPack(
                slotKey = "asr-${dir.name}",
                category = ModelCategory.ASR,
                engineImpl = "SHERPA_ONNX",
                source = ModelSource.USER_IMPORTED,
                rootDir = "asr/${dir.name}",
                primaryFile = tokens.name,
                sizeBytes = -1,
            )
        }
        // TTS
        val vits = dir.walkTopDown().firstOrNull { it.isFile && (it.name.endsWith(".onnx") || it.name.endsWith(".bin")) && dir.listFiles()?.any { it.name.contains("vits") } == true }
        if (vits != null && tokens == null) {
            out += ModelPack(
                slotKey = "tts-${dir.name}",
                category = ModelCategory.TTS,
                engineImpl = "SHERPA_ONNX_VITS",
                source = ModelSource.USER_IMPORTED,
                rootDir = "tts/${dir.name}",
                primaryFile = vits.name,
                sizeBytes = -1,
            )
        }
        return out
    }
}
