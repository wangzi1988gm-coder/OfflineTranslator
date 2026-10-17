package com.example.offlinetranslator.engine.translator

import com.example.offlinetranslator.data.Language
import com.example.offlinetranslator.data.LanguagePair
import com.example.offlinetranslator.data.ModelCategory
import com.example.offlinetranslator.data.ModelPack
import com.example.offlinetranslator.model.ModelManager

/**
 * 翻译引擎路由器：根据 (src, tgt) 选择最佳引擎。
 *
 * 查找规则：
 *  1. 优先查"直接模型"槽位（如 zh-en, en-zh, ru-tg 等）。
 *  2. 未命中直接槽位时，构造 CascadeTranslator（zh↔tg, en↔tg）。
 *  3. 完全没模型时，返回 FallbackTranslator（友好提示）。
 */
class TranslatorRouter(
    private val modelManager: ModelManager,
    private val marianFactory: () -> MarianMtEngine,
) {
    private val directEngines = HashMap<String, TranslationEngine>()
    private val cascadeEngines = HashMap<String, TranslationEngine>()
    private val fallback = FallbackTranslator()

    fun preloadAll() {
        // 加载所有已就绪的翻译引擎
        for (pack in modelManager.packsByCategory(ModelCategory.TRANSLATOR)) {
            loadEngine(pack)
        }
        rebuildCascade()
    }

    fun loadEngine(pack: ModelPack): TranslationEngine? {
        if (pack.engineImpl != "MARIAN_MT" && pack.engineImpl != "NLLB_200") return null
        val engine = marianFactory()
        if (engine.load(pack)) {
            directEngines[pack.slotKey] = engine
            rebuildCascade()
            return engine
        }
        return null
    }

    fun unloadEngine(slotKey: String) {
        directEngines.remove(slotKey)?.let { (it as? MarianMtEngine)?.unload() }
        rebuildCascade()
    }

    private fun rebuildCascade() {
        cascadeEngines.clear()

        // 中↔俄：当前模型包提供中↔英、英↔俄时，自动通过英语级联。
        val zhEn = directEngines["zh-en"]
        val enRu = directEngines["en-ru"]
        if (zhEn != null && enRu != null) {
            cascadeEngines["zh-ru"] = CascadeTranslator(zhEn, enRu, Language.EN)
        }
        val ruEn = directEngines["ru-en"]
        val enZh = directEngines["en-zh"]
        if (ruEn != null && enZh != null) {
            cascadeEngines["ru-zh"] = CascadeTranslator(ruEn, enZh, Language.EN)
        }

        // 中↔塔吉克：优先使用中文↔俄语与俄语↔塔吉克的直接模型组合。
        val zhRu = directEngines["zh-ru"] ?: cascadeEngines["zh-ru"]
        val ruTg = directEngines["ru-tg"]
        if (zhRu != null && ruTg != null) {
            cascadeEngines["zh-tg"] = CascadeTranslator(zhRu, ruTg, Language.RU)
        }
        val tgRu = directEngines["tg-ru"]
        val ruZh = directEngines["ru-zh"] ?: cascadeEngines["ru-zh"]
        if (tgRu != null && ruZh != null) {
            cascadeEngines["tg-zh"] = CascadeTranslator(tgRu, ruZh, Language.RU)
        }
    }

    fun pick(src: Language, tgt: Language): TranslationEngine {
        if (src == tgt) return IdentityTranslator()
        val pair = LanguagePair.of(src, tgt) ?: return fallback
        // 直接
        directEngines[pair.id]?.let { if (it.isReady) return it }
        // 级联
        cascadeEngines[pair.id]?.let { if (it.isReady) return it }
        return fallback
    }

    suspend fun translate(text: String, src: Language, tgt: Language): TranslationResult {
        if (text.isBlank()) return TranslationResult("", "EMPTY")
        val engine = pick(src, tgt)
        return engine.translate(text, src, tgt)
    }

    fun stats(): Map<String, Boolean> = buildMap {
        for (pair in LanguagePair.ALL) {
            put(pair.id, (directEngines[pair.id]?.isReady ?: false) || (cascadeEngines[pair.id]?.isReady ?: false))
        }
    }
}

/** 空翻译：src == tgt */
class IdentityTranslator : TranslationEngine {
    override val engineId = "IDENTITY"
    override val isReady = true
    override suspend fun translate(text: String, src: Language, tgt: Language): TranslationResult =
        TranslationResult(text, engineId)
}

/** 兜底翻译：模型未就绪 */
class FallbackTranslator : TranslationEngine {
    override val engineId = "FALLBACK"
    override val isReady = true
    override suspend fun translate(text: String, src: Language, tgt: Language): TranslationResult {
        return TranslationResult(
            translatedText = text,
            engineId = engineId,
            fallback = true,
            warning = "未找到 $src → $tgt 的离线模型。\n" +
                    "请到「模型」页导入：\n" +
                    "  1) 直接模型 (${src.iso639}-${tgt.iso639}) 或\n" +
                    "  2) 间接路径：${src.iso639}-ru + ru-${tgt.iso639}\n" +
                    "  3) 或中英 / 中俄 / 俄塔吉克模型组合",
        )
    }
}
