package com.example.offlinetranslator.data

import kotlinx.serialization.Serializable

/**
 * 模型分类。
 *  - TRANSLATOR：NMT 翻译模型（OPUS-MT / NLLB 等）
 *  - ASR：离线语音识别
 *  - OCR：文字识别（占位 — ML Kit 模型随 APK 自带）
 *  - TTS：离线朗读（VITS）
 */
@Serializable
enum class ModelCategory {
    TRANSLATOR,
    ASR,
    OCR,
    TTS,
}

/**
 * 模型"槽位"。
 *
 *  每个引擎能力 = 一个槽位。
 *  例：NMT 中文→英文模型槽位 = TranslatorSlot(pair=zh-en, impl=MARIAN_MT)
 *  一个槽位可被多个具体模型文件满足（如同时存在 marianzh-en.onnx 和 nllb-zh-en.int8）。
 */
@Serializable
data class ModelSlot(
    val category: ModelCategory,
    /** 模型子键，例如 "zh-en" / "ru-tg" / "asr-zh" */
    val key: String,
    /** 用户可读的标题，例如 "中文 → 英文（OPUS-MT）" */
    val displayName: String,
    /** 主要语言（仅展示） */
    val primaryLanguage: Language? = null,
    /** 描述、备注 */
    val note: String = "",
)

/**
 * 模型包元数据（运行时）。
 *
 * 描述已加载到磁盘的某个具体模型文件集合，包括：
 *  - 槽位（属于哪个槽位）
 *  - 引擎实现（决定如何加载）
 *  - 主文件路径（ONNX / sherpa-onnx / 目录）
 *  - 大小、来源（assets / 外部导入）
 *  - 已加载到内存的句柄
 */
@Serializable
data class ModelPack(
    val slotKey: String,
    val category: ModelCategory,
    val engineImpl: String,
    val source: ModelSource,
    val rootDir: String,
    val primaryFile: String,
    val sizeBytes: Long,
    val version: String = "1",
    val checksumSha256: String? = null,
    val notes: String = "",
) {
    fun displaySize(): String {
        val mb = sizeBytes / 1024.0 / 1024.0
        return if (mb < 1024) "%.1f MB".format(mb) else "%.2f GB".format(mb / 1024)
    }
}

@Serializable
enum class ModelSource {
    /** 来自 assets 内置 */
    ASSETS_BUILTIN,
    /** 来自应用外部存储 /Android/data 导入 */
    USER_IMPORTED,
    /** 来自系统/设备已装的语言包（系统 TTS） */
    SYSTEM,
}

/**
 * 内置槽位清单（覆盖 4 个语种，6 个翻译方向 + 4 个 ASR + 1 套 TTS）。
 */
object ModelSlots {

    // ----- 翻译 -----
    val TRANSLATOR: List<ModelSlot> = listOf(
        ModelSlot(ModelCategory.TRANSLATOR, "zh-en", "中文 → 英文（OPUS-MT）", Language.ZH),
        ModelSlot(ModelCategory.TRANSLATOR, "en-zh", "英文 → 中文（OPUS-MT）", Language.EN),
        ModelSlot(ModelCategory.TRANSLATOR, "zh-ru", "中文 → 俄文（OPUS-MT）", Language.ZH),
        ModelSlot(ModelCategory.TRANSLATOR, "ru-zh", "俄文 → 中文（OPUS-MT）", Language.RU),
        ModelSlot(ModelCategory.TRANSLATOR, "ru-tg", "俄文 → 塔吉克（OPUS-MT）", Language.RU, "塔吉克语通过俄文做级联桥接"),
        ModelSlot(ModelCategory.TRANSLATOR, "tg-ru", "塔吉克 → 俄文（OPUS-MT）", Language.TG, "塔吉克语通过俄文做级联桥接"),
    )

    // ----- ASR -----
    val ASR: List<ModelSlot> = listOf(
        ModelSlot(ModelCategory.ASR, "asr-zh", "中文识别（sherpa-onnx Paraformer）", Language.ZH),
        ModelSlot(ModelCategory.ASR, "asr-en", "英文识别（sherpa-onnx Whisper-Tiny）", Language.EN),
        ModelSlot(ModelCategory.ASR, "asr-ru", "俄文识别（sherpa-onnx）", Language.RU),
        ModelSlot(ModelCategory.ASR, "asr-tg", "塔吉克语识别（俄文模型兜底）", Language.TG, "塔吉克语 ASR 模型稀缺；用俄文 ASR 模型做兜底识别"),
    )

    // ----- TTS -----
    val TTS: List<ModelSlot> = listOf(
        ModelSlot(ModelCategory.TTS, "tts-zh", "中文朗读（VITS / 系统）", Language.ZH),
        ModelSlot(ModelCategory.TTS, "tts-en", "英文朗读（VITS / 系统）", Language.EN),
        ModelSlot(ModelCategory.TTS, "tts-ru", "俄文朗读（VITS / 系统）", Language.RU, "塔吉克语共用俄文 TTS 兜底"),
        ModelSlot(ModelCategory.TTS, "tts-tg", "塔吉克语朗读（俄文兜底）", Language.TG, "塔吉克语 TTS 暂缺，使用俄文 TTS 模型朗读西里尔字符"),
    )

    fun all(): List<ModelSlot> = TRANSLATOR + ASR + TTS

    fun findSlot(category: ModelCategory, key: String): ModelSlot? =
        all().firstOrNull { it.category == category && it.key == key }
}
