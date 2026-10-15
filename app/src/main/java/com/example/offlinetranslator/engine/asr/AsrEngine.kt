package com.example.offlinetranslator.engine.asr

import com.example.offlinetranslator.data.Language
import kotlinx.coroutines.flow.Flow

/**
 * 离线 ASR 引擎接口。
 *
 * 提供两种调用方式：
 *  - recognizeFile(audioPath): 同步识别 wav 文件
 *  - streaming(language): 返回 ASR 流式识别器，用于语音对讲
 */
interface AsrEngine {
    val engineId: String
    fun isReady(language: Language): Boolean

    suspend fun recognizeFile(audioPath: String, language: Language): String
    suspend fun recognizeSamples(samples: FloatArray, sampleRate: Int, language: Language): String

    /** 流式识别（用于对话模式） */
    fun createStreaming(language: Language): StreamingAsr
}

interface StreamingAsr {
    /** 接受一段 PCM（已重采样到 16 kHz mono） */
    fun acceptWaveform(samples: FloatArray, sampleRate: Int = 16_000)

    /** 立即解码并返回当前结果（含已识别部分文本） */
    fun decodeStep(): String

    /** 输入结束后填充尾部，得到完整结果 */
    fun finalize(): String

    fun reset()

    fun release()
}

/** 引擎未就绪异常 */
class AsrNotReadyException(message: String) : RuntimeException(message)

data class AsrPartialResult(val text: String, val isEndpoint: Boolean = false)
