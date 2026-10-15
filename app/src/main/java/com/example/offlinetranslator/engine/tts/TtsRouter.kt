package com.example.offlinetranslator.engine.tts

import android.content.Context
import com.example.offlinetranslator.data.Language
import com.example.offlinetranslator.data.ModelCategory
import com.example.offlinetranslator.data.TtsEnginePref
import com.example.offlinetranslator.model.ModelManager
import com.example.offlinetranslator.model.ModelStorage
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * TTS 路由器：按用户偏好选择 System / Offline VITS / 回退组合。
 *
 *  - SYSTEM_PREFERRED：优先系统 TTS；失败时回退离线 VITS。
 *  - OFFLINE_VITS_ONLY：仅离线 VITS；无模型时调用系统 TTS。
 *  - OFFLINE_VITS_FALLBACK_SYSTEM：先离线 VITS，无模型时用系统。
 */
class TtsRouter(
    private val context: Context,
    private val storage: ModelStorage,
    private val modelManager: ModelManager,
    private var preference: TtsEnginePref = TtsEnginePref.SYSTEM_PREFERRED,
) {
    private val system = SystemTtsEngine(context)
    private val offline = SherpaOnnxTtsEngine(context, storage)

    fun setPreference(p: TtsEnginePref) {
        preference = p
    }

    fun preload() {
        // 异步预热系统 TTS
        GlobalScope.launch { system.awaitReady() }

        // 加载离线 VITS（如果用户已导入模型）
        listOf(Language.ZH, Language.EN, Language.RU, Language.TG).forEach { lang ->
            val pack = modelManager.packFor(ModelCategory.TTS, "tts-${lang.iso639}")
            if (pack != null) offline.load(lang, pack)
            else if (lang == Language.TG) {
                // 用 ru 模型兜底（塔吉克语也是西里尔字符）
                modelManager.packFor(ModelCategory.TTS, "tts-ru")?.let { offline.load(Language.TG, it) }
            }
        }
    }

    suspend fun speak(text: String, language: Language, rate: Float = 1.0f, pitch: Float = 1.0f): TtsResult {
        return when (preference) {
            TtsEnginePref.OFFLINE_VITS_ONLY -> {
                if (offline.isReady(language)) offline.speak(text, language, rate, pitch)
                else system.speak(text, language, rate, pitch).copy(warning = "无离线 VITS 模型，使用系统 TTS 兜底")
            }
            TtsEnginePref.SYSTEM_PREFERRED -> {
                if (system.isReady(language)) system.speak(text, language, rate, pitch)
                else if (offline.isReady(language)) offline.speak(text, language, rate, pitch)
                else TtsResult(false, "NONE", language, warning = "系统 / 离线 TTS 都未就绪")
            }
            TtsEnginePref.OFFLINE_VITS_FALLBACK_SYSTEM -> {
                if (offline.isReady(language)) offline.speak(text, language, rate, pitch)
                else system.speak(text, language, rate, pitch)
            }
        }
    }

    fun stop() {
        system.stop()
        offline.stop()
    }

    fun release() {
        system.release()
        offline.release()
    }

    fun systemEngine(): SystemTtsEngine = system
    fun offlineEngine(): SherpaOnnxTtsEngine = offline
}
