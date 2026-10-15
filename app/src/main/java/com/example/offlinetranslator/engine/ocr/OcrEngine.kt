package com.example.offlinetranslator.engine.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.example.offlinetranslator.data.Language
import com.example.offlinetranslator.data.ScriptType

/**
 * 离线 OCR 引擎接口。
 *
 * 输入：
 *  - Bitmap（从相机/相册解码）
 *  - Uri
 *  - ByteArray（压缩图片数据）
 *
 * 输出：
 *  - OcrResult（含全文本 + 行级位置 + 语言/脚本检测）
 *
 * 引擎实现：
 *  - MlKitOcrEngine：基于 Google ML Kit Text Recognition v2（本地模型，APK 内置）。
 */
interface OcrEngine {
    val engineId: String
    suspend fun recognize(bitmap: Bitmap, hints: Set<Language> = emptySet()): OcrResult
    suspend fun recognize(uri: Uri, hints: Set<Language> = emptySet()): OcrResult
}

data class OcrResult(
    val fullText: String,
    val lines: List<OcrLine>,
    val engineId: String,
    val detectedScripts: Set<ScriptType> = emptySet(),
    val elapsedMillis: Long = 0,
)

data class OcrLine(
    val text: String,
    val boundingBox: Rect?,
    val confidence: Float,
    val script: ScriptType = ScriptType.UNKNOWN,
)

class OcrNotReadyException(message: String) : RuntimeException(message)
