package com.example.offlinetranslator.ui.screen.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinetranslator.data.Language
import com.example.offlinetranslator.data.LanguagePair
import com.example.offlinetranslator.engine.ocr.MlKitOcrEngine
import com.example.offlinetranslator.engine.ocr.OcrLine
import com.example.offlinetranslator.engine.ocr.OcrResult
import com.example.offlinetranslator.engine.translator.TranslationResult
import com.example.offlinetranslator.engine.translator.TranslatorRouter
import com.example.offlinetranslator.engine.tts.TtsRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 图片翻译 ViewModel。
 */
class ImageTranslateViewModel(
    private val ocr: MlKitOcrEngine,
    private val translator: TranslatorRouter,
    private val tts: TtsRouter,
) : ViewModel() {

    enum class Mode { GENERAL, SIGN, MENU, DOCUMENT }

    data class LineTranslation(
        val line: OcrLine,
        val translated: String,
        val fallback: Boolean = false,
    )

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap.asStateFlow()

    private val _fromLang = MutableStateFlow(Language.ZH)
    val fromLang: StateFlow<Language> = _fromLang.asStateFlow()

    private val _toLang = MutableStateFlow(Language.EN)
    val toLang: StateFlow<Language> = _toLang.asStateFlow()

    private val _mode = MutableStateFlow(Mode.GENERAL)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _ocr = MutableStateFlow<OcrResult?>(null)
    val ocrResult: StateFlow<OcrResult?> = _ocr.asStateFlow()

    private val _translations = MutableStateFlow<List<LineTranslation>>(emptyList())
    val translations: StateFlow<List<LineTranslation>> = _translations.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun setFrom(lang: Language) {
        _fromLang.value = lang
        if (!LanguagePair.isSupported(lang, _toLang.value)) {
            _toLang.value = if (lang == Language.ZH) Language.EN else Language.ZH
        }
    }
    fun setTo(lang: Language) {
        if (LanguagePair.isSupported(_fromLang.value, lang)) _toLang.value = lang
    }
    fun setMode(mode: Mode) { _mode.value = mode }

    fun loadFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            _status.value = "加载图片中…"
            try {
                val bmp = decodeSampled(context, uri)
                if (bmp == null) {
                    _status.value = "图片加载失败"
                    return@launch
                }
                _bitmap.value = bmp
                runOcr(bmp)
            } finally {
                _busy.value = false
            }
        }
    }

    fun loadFromBitmap(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            _bitmap.value = bitmap
            runOcr(bitmap)
        }
    }

    private suspend fun runOcr(bitmap: Bitmap) {
        _status.value = "OCR 识别中…"
        _busy.value = true
        try {
            val r = ocr.recognize(bitmap)
            _ocr.value = r
            _status.value = "识别到 ${r.lines.size} 行"
        } catch (e: Throwable) {
            _status.value = "OCR 失败: ${e.message}"
        } finally {
            _busy.value = false
        }
    }

    fun translateAll() {
        viewModelScope.launch {
            val r = _ocr.value ?: run {
                _status.value = "请先识别图片"
                return@launch
            }
            _busy.value = true
            _status.value = "翻译中…"
            val out = ArrayList<LineTranslation>()
            try {
                for ((idx, line) in r.lines.withIndex()) {
                    if (line.text.isBlank()) continue
                    val tr = translator.translate(line.text, _fromLang.value, _toLang.value)
                    out += LineTranslation(
                        line = line,
                        translated = tr.translatedText,
                        fallback = tr.fallback,
                    )
                    _status.value = "翻译 ${idx + 1}/${r.lines.size}"
                }
                _translations.value = out
                if (out.any { it.fallback }) {
                    _status.value = "部分行使用了回退翻译"
                } else {
                    _status.value = "翻译完成 · ${out.size} 行"
                }
            } catch (e: Throwable) {
                _status.value = "翻译失败: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * 生成双语图：在原图上叠加译文（覆盖原文字）。
     */
    fun generateOverlayBitmap(): Bitmap? {
        val src = _bitmap.value ?: return null
        val translations = _translations.value
        if (translations.isEmpty()) return null
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = out.height / 32f
            isAntiAlias = true
        }

        for (tr in translations) {
            val box = tr.line.boundingBox ?: continue
            val padding = 4
            val bg = Rect(
                box.left - padding,
                box.top - padding,
                box.right + padding,
                box.bottom + padding,
            )
            // 半透明白底
            paint.color = Color.argb(200, 255, 255, 255)
            canvas.drawRect(bg, paint)
            // 译文
            val lines = wrapText(tr.translated, textPaint, bg.width())
            var y = bg.top + textPaint.textSize
            for (s in lines) {
                canvas.drawText(s, bg.left.toFloat(), y, textPaint)
                y += textPaint.textSize * 1.2f
                if (y > bg.bottom) break
            }
        }
        return out
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        val out = mutableListOf<String>()
        var current = StringBuilder()
        for (ch in text) {
            current.append(ch)
            if (paint.measureText(current.toString()) > maxWidth) {
                // 回退一个字
                val s = current.toString().dropLast(1)
                if (s.isNotEmpty()) out += s
                current = StringBuilder(ch.toString())
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    private fun decodeSampled(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(ins, null, opts)
                val maxDim = 4096
                val sample = calculateSample(opts.outWidth, opts.outHeight, maxDim)
                val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = context.contentResolver.openInputStream(uri)?.use { ins2 ->
                    BitmapFactory.decodeStream(ins2, null, opts2)
                }
                // 处理 EXIF 旋转
                bmp?.let { rotateIfNeeded(context, uri, it) }
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun calculateSample(w: Int, h: Int, max: Int): Int {
        var sample = 1
        var longer = maxOf(w, h)
        while (longer / 2 >= max) {
            longer /= 2
            sample *= 2
        }
        return sample
    }

    private fun rotateIfNeeded(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val exif = ExifInterface(ins)
                val orient = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
                val matrix = android.graphics.Matrix()
                when (orient) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    else -> return bitmap
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } ?: bitmap
        } catch (_: Throwable) {
            bitmap
        }
    }

    fun speakAll() {
        viewModelScope.launch {
            val trs = _translations.value
            if (trs.isEmpty()) return@launch
            for ((i, tr) in trs.withIndex()) {
                tts.speak(tr.translated, _toLang.value)
                _status.value = "朗读 ${i + 1}/${trs.size}"
                kotlinx.coroutines.delay(50)
            }
        }
    }

    fun reset() {
        _bitmap.value = null
        _ocr.value = null
        _translations.value = emptyList()
        _status.value = null
    }
}
