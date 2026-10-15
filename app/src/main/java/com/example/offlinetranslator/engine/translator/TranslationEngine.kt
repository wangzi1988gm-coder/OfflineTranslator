package com.example.offlinetranslator.engine.translator

import com.example.offlinetranslator.data.Language

/**
 * 翻译引擎接口。
 *
 * 不同实现策略：
 *  - MarianMtEngine：ONNX 推理 OPUS-MT（Marian 架构）。
 *  - CascadeTranslator：把 zh↔tg / en↔tg 用 zh↔ru + ru↔tg 级联。
 *  - FallbackTranslator：模型缺失时返回友好提示（不抛异常）。
 */
interface TranslationEngine {
    val engineId: String
    val isReady: Boolean
    suspend fun translate(text: String, src: Language, tgt: Language): TranslationResult
}

data class TranslationResult(
    val translatedText: String,
    val engineId: String,
    val usedCascade: List<String> = emptyList(),
    val fallback: Boolean = false,
    val warning: String? = null,
    val elapsedMillis: Long = 0,
)

/** 引擎不可用异常 */
class EngineNotReadyException(message: String) : RuntimeException(message)
