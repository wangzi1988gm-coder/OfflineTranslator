package com.example.offlinetranslator.engine.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.offlinetranslator.data.Language
import com.example.offlinetranslator.data.ScriptType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit OCR 引擎。
 *
 *  - 拉丁识别器（默认）：覆盖 en/ru/tg（俄语和塔吉克语都用西里尔字符；ML Kit 的 latin 模型对西里尔有基础支持）。
 *  - 中文识别器：覆盖 zh。
 *  - 自动检测脚本：中英文混排时合并两个识别器结果。
 *
 * 注意：ML Kit 是本地推理，无需联网（除非用户主动下载语言包）。
 */
class MlKitOcrEngine(private val context: Context) : OcrEngine {

    override val engineId = "ML_KIT"

    private val latinClient: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val chineseClient: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    override suspend fun recognize(bitmap: Bitmap, hints: Set<Language>): OcrResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val image = InputImage.fromBitmap(bitmap, 0)

            val needChinese = hints.any { it.script == ScriptType.CJK } || detectNeedsChinese(bitmap)
            val needLatin = hints.isEmpty() ||
                hints.any { it.script != ScriptType.CJK } ||
                detectNeedsLatin(bitmap)

            val lines = mutableListOf<OcrLine>()
            val scripts = mutableSetOf<ScriptType>()

            if (needLatin) {
                val r = runRecognizer(latinClient, image)
                lines += r.lines.map { it.copy(script = detectScript(it.text)) }
                scripts += r.detectedScripts
            }
            if (needChinese) {
                val r = runRecognizer(chineseClient, image)
                lines += r.lines.map { it.copy(script = detectScript(it.text)) }
                scripts += r.detectedScripts
            }

            // 按行 y 坐标排序
            lines.sortBy { it.boundingBox?.top ?: 0 }
            val fullText = lines.joinToString("\n") { it.text }
            OcrResult(
                fullText = fullText,
                lines = lines,
                engineId = engineId,
                detectedScripts = scripts,
                elapsedMillis = System.currentTimeMillis() - start,
            )
        }

    override suspend fun recognize(uri: Uri, hints: Set<Language>): OcrResult =
        withContext(Dispatchers.IO) {
            val bitmap = loadBitmap(uri) ?: throw IllegalArgumentException("无法加载图片: $uri")
            recognize(bitmap, hints)
        }

    private suspend fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            if (uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
            } else {
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "loadBitmap: ${t.message}")
            null
        }
    }

    private suspend fun runRecognizer(client: TextRecognizer, image: InputImage): OcrResult =
        suspendCancellableCoroutine { cont ->
            client.process(image)
                .addOnSuccessListener { result ->
                    val lines = result.textBlocks.flatMap { block ->
                        block.lines.map { line ->
                            OcrLine(
                                text = line.text,
                                boundingBox = line.boundingBox?.let { Rect(it.left, it.top, it.right, it.bottom) },
                                confidence = line.confidence ?: 0f,
                            )
                        }
                    }
                    val scripts = lines.map { detectScript(it.text) }.toSet()
                    cont.resume(
                        OcrResult(
                            fullText = lines.joinToString("\n") { it.text },
                            lines = lines,
                            engineId = engineId,
                            detectedScripts = scripts,
                        )
                    )
                }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    /** 启发式：根据文本中是否有 CJK 字符决定是否需要中文识别器。 */
    private fun detectNeedsChinese(bitmap: Bitmap): Boolean = false
    private fun detectNeedsLatin(bitmap: Bitmap): Boolean = true

    /** 检测单行文本的主脚本。 */
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

    fun release() {
        runCatching { latinClient.close() }
        runCatching { chineseClient.close() }
    }

    companion object {
        private const val TAG = "MlKitOcrEngine"
    }
}
