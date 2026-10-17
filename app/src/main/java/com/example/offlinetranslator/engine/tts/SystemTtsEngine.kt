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

    override fun isReady(language: Language): Boolean =
        initialized && offlineVoiceFor(language) != null

    override suspend fun speak(text: String, language: Language, rate: Float, pitch: Float): TtsResult =
        suspendCancellableCoroutine { cont ->
            try {
                if (!initialized) {
                    cont.resume(TtsResult(false, engineId, language, warning = "TTS 未初始化"))
                    return@suspendCancellableCoroutine
                }
                val voice = offlineVoiceFor(language)
                    ?: run {
                        cont.resume(
                            TtsResult(
                                false, engineId, language,
                                warning = "没有可用的离线语音包：${language.displayName}。请安装本地语音数据或导入离线 TTS 模型。"
                            )
                        )
                        return@suspendCancellableCoroutine
                    }
                tts?.voice = voice
                val fallbackWarning = if (language == Language.TG) "塔吉克语系统语音不可用，使用俄语离线语音朗读" else null
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

    /** Only choose locally installed voices; never select a voice that requires network access. */
    private fun offlineVoiceFor(language: Language): android.speech.tts.Voice? {
        val target = localeFor(language)
        val voices = tts?.voices.orEmpty().filter { !it.isNetworkConnectionRequired }
        return voices.firstOrNull { it.locale == target }
            ?: voices.firstOrNull { it.locale.language == target.language }
    }

    companion object {
        private const val TAG = "SystemTtsEngine"
    }
}
