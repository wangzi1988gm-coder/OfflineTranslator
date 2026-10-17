package com.example.offlinetranslator.engine.translator

import com.example.offlinetranslator.data.Language
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CascadeTranslatorTest {
    private class RecordingEngine(
        override val engineId: String,
    ) : TranslationEngine {
        override val isReady: Boolean = true
        val calls = mutableListOf<Pair<Language, Language>>()

        override suspend fun translate(
            text: String,
            src: Language,
            tgt: Language,
        ): TranslationResult {
            calls += src to tgt
            return TranslationResult("$engineId($text)", engineId)
        }
    }

    @Test
    fun usesFirstHopThenSecondHopInCorrectDirection() = runTest {
        val chineseToRussian = RecordingEngine("zh-ru")
        val russianToTajik = RecordingEngine("ru-tg")
        val cascade = CascadeTranslator(chineseToRussian, russianToTajik, Language.RU)

        val result = cascade.translate("你好", Language.ZH, Language.TG)

        assertEquals(listOf(Language.ZH to Language.RU), chineseToRussian.calls)
        assertEquals(listOf(Language.RU to Language.TG), russianToTajik.calls)
        assertEquals("ru-tg(zh-ru(你好))", result.translatedText)
        assertEquals(listOf("zh->ru", "ru->tg"), result.usedCascade)
        assertFalse(result.fallback)
    }

    @Test
    fun stopsWhenFirstHopFallsBack() = runTest {
        val first = object : TranslationEngine {
            override val engineId = "missing-first-hop"
            override val isReady = true
            override suspend fun translate(text: String, src: Language, tgt: Language) =
                TranslationResult(text, engineId, fallback = true, warning = "missing model")
        }
        val second = RecordingEngine("ru-tg")
        val result = CascadeTranslator(first, second, Language.RU)
            .translate("你好", Language.ZH, Language.TG)

        assertTrueFallback(result.fallback)
        assertEquals(emptyList<Pair<Language, Language>>(), second.calls)
    }

    private fun assertTrueFallback(value: Boolean) {
        org.junit.Assert.assertTrue(value)
    }
}
