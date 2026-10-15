package com.example.offlinetranslator.ui.screen.translate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinetranslator.data.AppSettings
import com.example.offlinetranslator.data.HistoryEntity
import com.example.offlinetranslator.data.Language
import com.example.offlinetranslator.data.SettingsCodec
import com.example.offlinetranslator.data.TtsEnginePref
import com.example.offlinetranslator.engine.asr.AsrRouter
import com.example.offlinetranslator.engine.ocr.MlKitOcrEngine
import com.example.offlinetranslator.engine.translator.TranslationResult
import com.example.offlinetranslator.engine.translator.TranslatorRouter
import com.example.offlinetranslator.engine.tts.TtsRouter
import com.example.offlinetranslator.data.HistoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 文本翻译 ViewModel。
 */
class TranslateViewModel(
    private val translator: TranslatorRouter,
    private val asr: AsrRouter,
    private val tts: TtsRouter,
    private val ocr: MlKitOcrEngine,
    private val historyDao: HistoryDao,
    private val settingsProvider: () -> AppSettings,
    private val settingsSaver: (AppSettings) -> Unit,
) : ViewModel() {

    private val _source = MutableStateFlow("")
    val source: StateFlow<String> = _source.asStateFlow()

    private val _result = MutableStateFlow<TranslationResult?>(null)
    val result: StateFlow<TranslationResult?> = _result.asStateFlow()

    private val _from = MutableStateFlow(Language.ZH)
    val from: StateFlow<Language> = _from.asStateFlow()

    private val _to = MutableStateFlow(Language.EN)
    val to: StateFlow<Language> = _to.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun setSource(text: String) {
        _source.value = text
    }

    fun setFrom(lang: Language) {
        _from.value = lang
    }

    fun setTo(lang: Language) {
        _to.value = lang
    }

    fun swap() {
        val a = _from.value
        _from.value = _to.value
        _to.value = a
        // 翻译结果保留方向时清空
        _result.value = null
    }

    fun translate() {
        viewModelScope.launch {
            val text = _source.value.trim()
            if (text.isEmpty()) {
                _status.value = "请先输入文字"
                return@launch
            }
            _busy.value = true
            _status.value = "翻译中…"
            try {
                val r = translator.translate(text, _from.value, _to.value)
                _result.value = r
                if (r.fallback) {
                    _status.value = r.warning
                } else {
                    _status.value = "完成 · ${r.engineId} · ${r.elapsedMillis} ms"
                    // 写入历史
                    historyDao.insert(
                        HistoryEntity(
                            fromLang = _from.value.iso639,
                            toLang = _to.value.iso639,
                            source = text,
                            translation = r.translatedText,
                            engine = r.engineId,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            } catch (e: Throwable) {
                _status.value = "失败: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun speak(target: SpeakTarget) {
        viewModelScope.launch {
            val text = when (target) {
                SpeakTarget.SOURCE -> _source.value
                SpeakTarget.TRANSLATION -> _result.value?.translatedText ?: return@launch
            }
            if (text.isBlank()) return@launch
            val lang = when (target) {
                SpeakTarget.SOURCE -> _from.value
                SpeakTarget.TRANSLATION -> _to.value
            }
            val r = tts.speak(text, lang)
            _status.value = if (r.warning != null) r.warning else "已朗读 (${r.engineId})"
        }
    }

    fun pasteFromClipboard(context: Context) {
        viewModelScope.launch(Dispatchers.Main) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            if (text.isNotBlank()) {
                _source.value = text
                _status.value = "已粘贴 ${text.length} 字"
            }
        }
    }

    fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("translation", text))
        _status.value = "已复制"
    }

    fun clear() {
        _source.value = ""
        _result.value = null
        _status.value = null
    }

    fun applyOcrText(text: String) {
        _source.value = text
    }

    enum class SpeakTarget { SOURCE, TRANSLATION }
}
