package com.example.offlinetranslator.engine.tts

import com.example.offlinetranslator.data.Language
import kotlinx.coroutines.flow.Flow

/**
 * 离线 TTS 引擎接口。
 *
 * speak() 调用：
 *  - 同步返回朗读结束时机（done Flow）。
 *  - 提供 stop() 立即停止当前朗读。
 */
interface TtsEngine {
    val engineId: String
    fun isReady(language: Language): Boolean
    suspend fun speak(text: String, language: Language, rate: Float = 1.0f, pitch: Float = 1.0f): TtsResult
    fun stop()
    fun release()
}

data class TtsResult(
    val spoken: Boolean,
    val engineId: String,
    val language: Language,
    val elapsedMillis: Long = 0,
    val warning: String? = null,
)

class TtsNotReadyException(message: String) : RuntimeException(message)
