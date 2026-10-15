package com.example.offlinetranslator.engine.asr

import android.content.Context
import com.example.offlinetranslator.data.Language
import com.example.offlinetranslator.data.ModelCategory
import com.example.offlinetranslator.model.ModelManager
import com.example.offlinetranslator.model.ModelStorage
import java.io.File

/**
 * ASR 路由器。
 *
 *  - 优先加载用户已就绪的 sherpa-onnx 模型。
 *  - 塔吉克语（tg）无原生模型时，回退到俄语（ru）ASR 模型（同为西里尔字母）。
 */
class AsrRouter(
    private val context: Context,
    private val storage: ModelStorage,
    private val modelManager: ModelManager,
) {
    private val engine = SherpaOnnxAsrEngine(context, storage)

    fun preload() {
        // 优先 zh / en / ru；tg → ru
        listOf(Language.ZH, Language.EN, Language.RU, Language.TG).forEach { lang ->
            val key = "asr-${lang.iso639}"
            val pack = modelManager.packFor(ModelCategory.ASR, key)
            if (pack != null) {
                engine.load(lang, pack)
            } else if (lang == Language.TG) {
                // 用 ru 模型兜底
                val ruPack = modelManager.packFor(ModelCategory.ASR, "asr-ru")
                if (ruPack != null) engine.load(Language.TG, ruPack)
            }
        }
    }

    fun get(): AsrEngine = engine

    fun isReady(language: Language): Boolean = engine.isReady(language)

    fun releaseAll() = engine.releaseAll()
}
