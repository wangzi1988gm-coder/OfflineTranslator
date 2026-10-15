package com.example.offlinetranslator.engine.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.offlinetranslator.data.Language
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Android 系统 TTS 引擎。
 *
 * 优点：
 *  - 音色质量高（厂商预装的 TTS 引擎如华为 TTS、小爱、讯飞通常更好）。
 *  - 无需打包模型。
 *
 * 局限：
 *  - 是否"离线"取决于用户是否安装了对应语言的离线语音包。
 *  - 塔吉克语（tg）系统通常不支持，调用时回退到俄语。
 */
class SystemTtsEngine(private val context: Context) : TtsEngine {

    override val engineId = "SYSTEM"

    @Volatile private var initialized = false
    private var tts: TextToSpeech? = null

    private val initResult = kotlinx.coroutines.CompletableDeferred<Boolean>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            initialized = status == TextToSpeech.SUCCESS
            initResult.complete(initialized)
            if (initialized) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    @Deprecated("Deprecated in API 21+")
                    override fun onError(utteranceId: String?) {}
                    override fun onError(utteranceId: String?, errorCode: Int) {}
                })
            }
        }
    }

    suspend fun awaitReady(): Boolean = initResult.await()

    override fun isReady(language: Language): Boolean {
        if (!initialized) return false
        val loc = localeFor(language)
        val res = tts?.setLanguage(loc) ?: TextToSpeech.LANG_NOT_SUPPORTED
        return res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
    }

    override suspend fun speak(text: String, language: Language, rate: Float, pitch: Float): TtsResult =
        suspendCancellableCoroutine { cont ->
            try {
                if (!initialized) {
                    cont.resume(TtsResult(false, engineId, language, warning = "TTS 未初始化"))
                    return@suspendCancellableCoroutine
                }
                val loc = localeFor(language)
                val res = tts?.setLanguage(loc) ?: TextToSpeech.LANG_NOT_SUPPORTED
                val fallbackWarning: String? = when (res) {
                    TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                        // 塔吉克语回退到俄语
                        if (language == Language.TG) {
                            tts?.setLanguage(Locale("ru"))
                            "塔吉克语未支持，使用俄语朗读"
                        } else {
                            "系统 TTS 未安装 ${language.displayName} 语音数据"
                        }
                    }
                    else -> null
                }
                tts?.setSpeechRate(rate)
                tts?.setPitch(pitch)
                val utteranceId = "utt-${System.currentTimeMillis()}"
                val start = System.currentTimeMillis()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                cont.resume(
                    TtsResult(
                        spoken = true,
                        engineId = engineId,
                        language = language,
                        elapsedMillis = System.currentTimeMillis() - start,
                        warning = fallbackWarning,
                    )
                )
            } catch (t: Throwable) {
                Log.e(TAG, "speak 失败: ${t.message}")
                cont.resume(TtsResult(false, engineId, language, warning = t.message))
            }
        }

    override fun stop() {
        tts?.stop()
    }

    override fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun localeFor(lang: Language): Locale = when (lang) {
        Language.ZH -> Locale.SIMPLIFIED_CHINESE
        Language.EN -> Locale.US
        Language.RU -> Locale("ru", "RU")
        Language.TG -> Locale("ru", "RU") // tg 回退到 ru
    }

    companion object {
        private const val TAG = "SystemTtsEngine"
    }
}
