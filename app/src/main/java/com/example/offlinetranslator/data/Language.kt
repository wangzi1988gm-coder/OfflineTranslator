package com.example.offlinetranslator.data

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * 应用支持的 4 种语言。
 *
 * 每个语言携带：
 *  - displayName：默认 UI 名称（简体中文）
 *  - englishName：英文名
 *  - nativeName：原文写法（用于 UI 显示 "Русский" / "Тоҷикӣ" 等）
 *  - bcp47：IETF BCP-47（OCR、TTS、Locale 共用）
 *  - iso639：ISO 639-1（zh / en / ru / tg）
 *  - script：主书写脚本（用于 OCR 引擎选择）
 *  - flagEmoji：纯展示用图标
 *  - isRtl：是否从右到左书写（本应用 4 种语言均非 RTL）
 */
@Serializable
enum class Language(
    val iso639: String,
    val bcp47: String,
    val displayName: String,
    val englishName: String,
    val nativeName: String,
    val script: ScriptType,
    val flagEmoji: String,
    val isRtl: Boolean = false,
) {
    ZH(
        iso639 = "zh",
        bcp47 = "zh-CN",
        displayName = "简体中文",
        englishName = "Chinese (Simplified)",
        nativeName = "简体中文",
        script = ScriptType.CJK,
        flagEmoji = "🇨🇳",
    ),
    EN(
        iso639 = "en",
        bcp47 = "en-US",
        displayName = "英语",
        englishName = "English",
        nativeName = "English",
        script = ScriptType.LATIN,
        flagEmoji = "🇬🇧",
    ),
    RU(
        iso639 = "ru",
        bcp47 = "ru-RU",
        displayName = "俄语",
        englishName = "Russian",
        nativeName = "Русский",
        script = ScriptType.CYRILLIC,
        flagEmoji = "🇷🇺",
    ),
    TG(
        iso639 = "tg",
        bcp47 = "tg-Cyrl",
        displayName = "塔吉克语",
        englishName = "Tajik",
        nativeName = "Тоҷикӣ",
        script = ScriptType.CYRILLIC,
        // 塔吉克斯坦国旗；阿富汗为塔吉克语次要使用国，这里仅取主旗
        flagEmoji = "🇹🇯",
    );

    fun toJavaLocale(): Locale = Locale.forLanguageTag(bcp47)

    companion object {
        /** 从 ISO 639-1 反查；找不到则回退 EN。 */
        fun fromIso639(iso: String?): Language =
            values().firstOrNull { it.iso639.equals(iso, ignoreCase = true) } ?: EN

        /** 从 BCP-47 反查。 */
        fun fromBcp47(tag: String?): Language {
            if (tag.isNullOrBlank()) return ZH
            val lower = tag.lowercase()
            return values().firstOrNull { it.bcp47.lowercase() == lower || it.iso639 == lower.substringBefore("-") }
                ?: ZH
        }
    }
}

@Serializable
enum class ScriptType {
    CJK,
    LATIN,
    CYRILLIC,
    ARABIC,
    UNKNOWN,
}

/**
 * 6 个翻译方向。中↔英、中↔俄、中↔塔吉克。
 */
@Serializable
data class LanguagePair(val from: Language, val to: Language) {
    init {
        require(from != to) { "from and to must differ" }
        require(isSupported(from, to)) { "不支持的翻译方向: $from -> $to" }
    }

    val id: String get() = "${from.iso639}-${to.iso639}"

    companion object {
        val ALL: List<LanguagePair> = listOf(
            LanguagePair(Language.ZH, Language.EN),
            LanguagePair(Language.EN, Language.ZH),
            LanguagePair(Language.ZH, Language.RU),
            LanguagePair(Language.RU, Language.ZH),
            LanguagePair(Language.ZH, Language.TG),
            LanguagePair(Language.TG, Language.ZH),
        )

        fun isSupported(from: Language, to: Language): Boolean {
            if (from == to) return false
            return ALL.any { it.from == from && it.to == to }
        }

        fun of(from: Language, to: Language): LanguagePair? =
            ALL.firstOrNull { it.from == from && it.to == to }
    }
}
