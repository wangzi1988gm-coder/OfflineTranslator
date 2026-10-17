package com.example.offlinetranslator.engine.translator

import com.example.offlinetranslator.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 两段式离线级联翻译器。
 *
 * primary 必须是 src → bridgeLang 的模型，bridge 必须是 bridgeLang → tgt 的模型。
 * 由 TranslatorRouter 按具体方向创建实例，避免把反方向模型误用于当前翻译。
 */
class CascadeTranslator(
    private val primary: TranslationEngine,
    private val bridge: TranslationEngine,
    private val bridgeLang: Language,
) : TranslationEngine {

    override val engineId: String = "CASCADE(${primary.engineId} -> ${bridge.engineId})"

    override val isReady: Boolean
        get() = primary.isReady && bridge.isReady

    override suspend fun translate(text: String, src: Language, tgt: Language): TranslationResult =
        withContext(Dispatchers.Default) {
            require(src != bridgeLang && tgt != bridgeLang) {
                "级联翻译的源语言和目标语言不能是桥接语言"
            }
            val mid = primary.translate(text, src, bridgeLang)
            if (mid.fallback) return@withContext mid.copy(
                engineId = engineId,
                warning = mid.warning ?: "第一段离线模型不可用"
            )

            val final = bridge.translate(mid.translatedText, bridgeLang, tgt)
            final.copy(
                usedCascade = listOf(
                    "${src.iso639}->${bridgeLang.iso639}",
                    "${bridgeLang.iso639}->${tgt.iso639}",
                ),
                engineId = engineId,
                warning = listOfNotNull(mid.warning, final.warning)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                    .ifBlank { "本次翻译使用两段离线级联" },
            )
        }
}
