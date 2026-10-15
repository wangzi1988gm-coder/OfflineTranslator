package com.example.offlinetranslator.util

import com.example.offlinetranslator.data.Language
import com.example.offlinetranslator.data.ScriptType

object LocaleHelper {

    /**
     * 检测文本的主脚本（用于 OCR 引擎选择、UI 提示）。
     */
    fun detectScript(text: String): ScriptType {
        if (text.isBlank()) return ScriptType.UNKNOWN
        var cjk = 0; var cyr = 0; var lat = 0
        for (c in text) {
            when {
                c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF -> cjk++
                c.code in 0x0400..0x04FF -> cyr++
                c.code in 0x0041..0x005A || c.code in 0x0061..0x007A || c.code in 0x00C0..0x024F -> lat++
            }
        }
        val max = maxOf(cjk, cyr, lat)
        return when {
            max == 0 -> ScriptType.UNKNOWN
            cjk == max -> ScriptType.CJK
            cyr == max -> ScriptType.CYRILLIC
            else -> ScriptType.LATIN
        }
    }

    /**
     * 从 BCP-47 字符串反查 Language。
     */
    fun langFromBcp47(tag: String?): Language {
        if (tag.isNullOrBlank()) return Language.ZH
        val lower = tag.lowercase()
        return Language.values().firstOrNull { it.bcp47.lowercase() == lower || it.iso639 == lower.substringBefore("-") }
            ?: Language.ZH
    }

    /**
     * 塔吉克语字符助手：用户输入时显示的提示符号。
     * Cyrillic Tajik 使用的额外字符：
     *   Ҷ ҳ Ғ Ӣ Ӯ Қ Ӯ (Ҷ, Ҳ, Ғ, Ӣ, Ӯ, Қ)
     * 上面这些不在标准俄语字母表里，常见于塔吉克语。
     */
    val tajikExtraChars = charArrayOf('Ҷ', 'Ҳ', 'Ғ', 'Ӣ', 'Ӯ', 'Қ')
}
