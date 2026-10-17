package com.example.offlinetranslator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagePairTest {
    @Test
    fun allRequiredTranslationDirectionsAreAvailable() {
        val expected = setOf(
            "zh-en", "en-zh",
            "zh-ru", "ru-zh",
            "zh-tg", "tg-zh",
        )

        assertEquals(expected, LanguagePair.ALL.map { it.id }.toSet())
        expected.forEach { id ->
            val pair = LanguagePair.ALL.first { it.id == id }
            assertNotNull(LanguagePair.of(pair.from, pair.to))
            assertTrue(LanguagePair.isSupported(pair.from, pair.to))
        }
    }

    @Test
    fun sameLanguageAndUnsupportedDirectionsAreRejected() {
        assertNull(LanguagePair.of(Language.ZH, Language.ZH))
        assertNull(LanguagePair.of(Language.EN, Language.TG))
        assertFalse(LanguagePair.isSupported(Language.EN, Language.TG))
    }

    @Test
    fun languageCodesAreCaseInsensitive() {
        assertEquals(Language.ZH, Language.fromIso639("ZH"))
        assertEquals(Language.RU, Language.fromBcp47("ru-RU"))
        assertEquals(Language.TG, Language.fromBcp47("tg-Cyrl"))
    }
}
