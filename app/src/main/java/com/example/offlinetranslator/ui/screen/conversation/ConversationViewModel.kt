package com.example.offlinetranslator.ui.screen.conversation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinetranslator.data.Language
import com.example.offlinetranslator.data.LanguagePair
import com.example.offlinetranslator.engine.asr.AsrRouter
import com.example.offlinetranslator.engine.asr.AudioRecorder
import com.example.offlinetranslator.engine.asr.StreamingAsr
import com.example.offlinetranslator.engine.translator.TranslatorRouter
import com.example.offlinetranslator.engine.tts.TtsRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.max

/**
 * 对话模式 ViewModel。
 *
 * 状态机：
 *  IDLE → (按住按钮 / 自动开始) → RECORDING → PROCESSING → SPEAKING → IDLE
 */
class ConversationViewModel(
    private val context: Context,
    private val asr: AsrRouter,
    private val translator: TranslatorRouter,
    private val tts: TtsRouter,
) : ViewModel() {

    enum class Phase { IDLE, RECORDING, PROCESSING, SPEAKING }
    enum class Speaker { A, B }

    data class Message(
        val speaker: Speaker,
        val lang: Language,
        val source: String,
        val translation: String,
    )

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _speaker = MutableStateFlow(Speaker.A)
    val speaker: StateFlow<Speaker> = _speaker.asStateFlow()

    private val _langA = MutableStateFlow(Language.ZH)
    val langA: StateFlow<Language> = _langA.asStateFlow()

    private val _langB = MutableStateFlow(Language.EN)
    val langB: StateFlow<Language> = _langB.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _partialSource = MutableStateFlow("")
    val partialSource: StateFlow<String> = _partialSource.asStateFlow()

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val recorder = AudioRecorder(context)
    private var streamingAsr: StreamingAsr? = null
    private var recordJob: Job? = null
    private var levelJob: Job? = null
    private var pcmBuffer = ArrayList<Float>(16_000 * 4) // 4 秒缓冲

    fun setLangA(lang: Language) {
        _langA.value = lang
        if (!LanguagePair.isSupported(lang, _langB.value)) {
            _langB.value = if (lang == Language.ZH) Language.EN else Language.ZH
        }
    }
    fun setLangB(lang: Language) {
        if (LanguagePair.isSupported(_langA.value, lang)) _langB.value = lang
    }
    fun swap() {
        val a = _langA.value
        _langA.value = _langB.value
        _langB.value = a
    }
    fun toggleSpeaker() {
        _speaker.value = if (_speaker.value == Speaker.A) Speaker.B else Speaker.A
    }

    /** 按下开始说话 */
    fun startRecording() {
        if (_phase.value != Phase.IDLE) return
        val speakerLang = currentSpeakerLang()
        if (!asr.isReady(speakerLang)) {
            _status.value = "本地 ASR 未就绪: ${speakerLang.displayName}"
            return
        }
        pcmBuffer.clear()
        _partialSource.value = ""
        _level.value = 0f
        _phase.value = Phase.RECORDING
        _status.value = "聆听中…"

        streamingAsr = try {
            asr.get().createStreaming(speakerLang)
        } catch (e: Throwable) {
            _status.value = "ASR 创建失败: ${e.message}"
            _phase.value = Phase.IDLE
            return
        }

        // 先订阅 PCM 流，再启动录音（避免竞态）
        recordJob = viewModelScope.launch(Dispatchers.Default) {
            recorder.pcmFlow.collect { chunk ->
                pcmBuffer.ensureCapacity(pcmBuffer.size + chunk.size)
                for (s in chunk) pcmBuffer.add(s)
                val peak = chunk.maxOfOrNull { abs(it) } ?: 0f
                _level.value = max(_level.value * 0.7f, peak)
                streamingAsr?.acceptWaveform(chunk, 16_000)
                _partialSource.value = streamingAsr?.decodeStep().orEmpty()
            }
        }

        // 启动录音
        val ok = recorder.start()
        if (!ok) {
            _status.value = "无法启动麦克风"
            _phase.value = Phase.IDLE
            streamingAsr?.release()
            streamingAsr = null
            recordJob?.cancel()
            recordJob = null
            return
        }
    }

    /** 松开停止并处理 */
    fun stopRecordingAndProcess() {
        if (_phase.value != Phase.RECORDING) return
        recorder.stop()
        recordJob?.cancel()
        recordJob = null

        _phase.value = Phase.PROCESSING
        _status.value = "识别中…"
        viewModelScope.launch {
            val speakerLang = currentSpeakerLang()
            val targetLang = currentTargetLang()
            try {
                val text = streamingAsr?.finalize().orEmpty().ifBlank {
                    // 兜底：把缓存的 PCM 一次性识别
                    if (pcmBuffer.isNotEmpty()) {
                        asr.get().recognizeSamples(pcmBuffer.toFloatArray(), 16_000, speakerLang)
                    } else ""
                }
                streamingAsr?.release()
                streamingAsr = null
                if (text.isBlank()) {
                    _status.value = "未识别到内容"
                    _phase.value = Phase.IDLE
                    return@launch
                }
                // 翻译
                val translation = translator.translate(text, speakerLang, targetLang)
                // 朗读翻译
                _phase.value = Phase.SPEAKING
                _status.value = "朗读中…"
                val ttsResult = tts.speak(translation.translatedText, targetLang)
                // 写入消息
                _messages.value = _messages.value + Message(
                    speaker = _speaker.value,
                    lang = speakerLang,
                    source = text,
                    translation = translation.translatedText,
                )
                if (translation.fallback) {
                    _status.value = translation.warning
                } else if (ttsResult.warning != null) {
                    _status.value = ttsResult.warning
                } else {
                    _status.value = "完成"
                }
                _partialSource.value = ""
                _level.value = 0f
                // 切到下一说话方
                toggleSpeaker()
            } catch (e: Throwable) {
                _status.value = "失败: ${e.message}"
            } finally {
                _phase.value = Phase.IDLE
            }
        }
    }

    fun cancel() {
        recorder.stop()
        recordJob?.cancel()
        recordJob = null
        streamingAsr?.release()
        streamingAsr = null
        _partialSource.value = ""
        _level.value = 0f
        _phase.value = Phase.IDLE
        _status.value = "已取消"
    }

    fun replayLast() {
        val last = _messages.value.lastOrNull() ?: return
        val targetLang = if (last.speaker == Speaker.A) _langB.value else _langA.value
        viewModelScope.launch { tts.speak(last.translation, targetLang) }
    }

    fun clearHistory() {
        _messages.value = emptyList()
    }

    private fun currentSpeakerLang(): Language =
        if (_speaker.value == Speaker.A) _langA.value else _langB.value

    private fun currentTargetLang(): Language =
        if (_speaker.value == Speaker.A) _langB.value else _langA.value

    override fun onCleared() {
        super.onCleared()
        recorder.stop()
        streamingAsr?.release()
        streamingAsr = null
    }
}
