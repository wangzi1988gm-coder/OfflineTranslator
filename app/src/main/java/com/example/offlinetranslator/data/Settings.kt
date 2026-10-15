package com.example.offlinetranslator.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 应用设置（持久化到 DataStore）。
 *
 * 字段尽量轻量；复杂状态（模型清单、加载状态）使用 ModelManager 单独管理。
 */
@Serializable
data class AppSettings(
    val defaultFrom: Language = Language.ZH,
    val defaultTo: Language = Language.EN,
    /** TTS 引擎偏好。 */
    val ttsEngine: TtsEnginePref = TtsEnginePref.SYSTEM_PREFERRED,
    /** 主题。 */
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    /** 是否启用 ASR 端点检测（VAD） */
    val useVad: Boolean = true,
    /** ASR 麦克风增益 0.5 ~ 2.0 */
    val micGain: Float = 1.0f,
    /** 是否启用模型占位回退（无模型时显示更友好的提示） */
    val gracefulFallback: Boolean = true,
    /** 是否启用图片翻译结果覆盖原图（双语图） */
    val overlayOnImage: Boolean = true,
    /** 文本方向（横屏时） */
    val allowLandscape: Boolean = false,
)

@Serializable
enum class TtsEnginePref {
    /** 系统 TTS（首选，质量好，依赖设备语音包） */
    SYSTEM_PREFERRED,
    /** 仅离线 VITS */
    OFFLINE_VITS_ONLY,
    /** 先尝试离线 VITS，失败回退系统 */
    OFFLINE_VITS_FALLBACK_SYSTEM,
}

@Serializable
enum class ThemeMode {
    LIGHT,
    DARK,
    FOLLOW_SYSTEM,
}

/**
 * 把 AppSettings 转成可在 DataStore 序列化的字符串。
 */
object SettingsCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(settings: AppSettings): String = json.encodeToString(AppSettings.serializer(), settings)
    fun decode(text: String?): AppSettings =
        if (text.isNullOrBlank()) AppSettings()
        else runCatching { json.decodeFromString(AppSettings.serializer(), text) }.getOrDefault(AppSettings())
}
