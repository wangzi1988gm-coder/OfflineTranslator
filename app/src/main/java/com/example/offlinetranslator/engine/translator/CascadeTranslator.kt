package com.example.offlinetranslator.engine.translator

import com.example.offlinetranslator.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 级联翻译器：用于没有直接 NMT 模型的语言对。
 *
 *  - zh ↔ tg：通过 zh ↔ ru + ru ↔ tg 两段级联
 *  - en ↔ tg：通过 en ↔ ru + ru ↔ tg 两段级联
 *  - zh ↔ tg 也可以 zhhui ↔ en + en ↔ ru + ru ↔ tg 三段级联（如果仅 zh ↔ ru + ru ↔ tg 可用）
 *
 *  注意：每段翻译会放大误差；本实现尽量选两段而非三段路径。
 */
class CascadeTranslator(
    private val primary: TranslationEngine,
    private val bridge: TranslationEngine,
    private val bridgeLang: Language,
) : TranslationEngine {

    override val engineId: String = "CASCADE(${primary.engineId} <-> ${bridge.engineId})"

    override val isReady: Boolean
        get() = primary.isReady && bridge.isReady

    override suspend fun translate(text: String, src: Language, tgt: Language): TranslationResult =
        withContext(Dispatchers.Default) {
            // 选择级联路径
            val (step1, step2) = pickPath(src, tgt)
            val mid = translateOnce(text, src, step1)
            val final = translateOnce(mid.translatedText, step1, step2)
            final.copy(
                translatedText = final.translatedText,
                usedCascade = listOf("${src.iso639}->${step1.iso639}", "${step1.iso639}->${step2.iso639}"),
                engineId = engineId,
                warning = "本次翻译使用级联：${src.iso639}→${step1.iso639}→${step2.iso639}",
            )
        }

    private suspend fun translateOnce(text: String, src: Language, tgt: Language): TranslationResult {
        return when {
            src == tgt -> TranslationResult(text, "IDENTITY")
            (src == Language.ZH && tgt == Language.RU) || (src == Language.RU && tgt == Language.ZH)
                || (src == Language.ZH && tgt == Language.EN) || (src == Language.EN && tgt == Language.ZH)
                || (src == Language.RU && tgt == Language.TG) || (src == Language.TG && tgt == Language.RU) -> primary.translate(text, src, tgt)
            else -> bridge.translate(text, src, tgt)
        }
    }

    /**
     *  根据 (src, tgt) 返回最优级联路径的两跳中间语言。
     *  已支持的直接路径：
     *   - ZH ↔ EN, ZH ↔ RU, RU ↔ TG
     *  需要级联：
     *   - ZH ↔ TG: ZH → RU → TG
     *   - EN ↔ TG: EN → RU → TG
     */
    private fun pickPath(src: Language, tgt: Language): Pair<Language, Language> {
        return when {
            src == Language.ZH && tgt == Language.TG -> Language.RU to Language.TG
            src == Language.TG && tgt == Language.ZH -> Language.RU to Language.ZH
            src == Language.EN && tgt == Language.TG -> Language.RU to Language.TG
            src == Language.TG && tgt == Language.EN -> Language.RU to Language.EN
            else -> {
                // 默认尝试 ZH 作为桥梁
                Language.ZH to tgt
            }
        }
    }
}
