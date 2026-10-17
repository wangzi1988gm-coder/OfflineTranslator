package com.example.offlinetranslator.engine.translator

import com.example.offlinetranslator.engine.translator.tokenize.SentencePieceLite
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SentencePieceLiteTest {
    @Test
    fun choosesHighestScoringPieceSequence() {
        val tokenizer = SentencePieceLite(
            listOf("<unk>", "</s>", "▁hello", "▁he", "llo"),
            floatArrayOf(-100f, -1f, -2f, -0.1f, -0.1f),
        )

        // The two-piece path scores -0.2, which is better than the single-piece path (-2).
        assertArrayEquals(intArrayOf(3, 4, 1), tokenizer.encode("hello"))
    }

    @Test
    fun unknownCharactersDoNotPreventEncoding() {
        val tokenizer = SentencePieceLite(
            listOf("<unk>", "</s>", "▁hi"),
            floatArrayOf(-100f, -1f, -0.1f),
        )

        val encoded = tokenizer.encode("hi🙂")
        assertEquals(1, encoded.last())
        assertEquals(3, encoded.size)
    }
}
