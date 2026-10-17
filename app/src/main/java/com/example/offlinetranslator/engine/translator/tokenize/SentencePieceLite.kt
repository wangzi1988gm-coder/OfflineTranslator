package com.example.offlinetranslator.engine.translator.tokenize

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.ln

/**
 * SentencePiece Unigram 模型极简读取器（仅解码）。
 *
 * 完整 SentencePiece (https://github.com/google/sentencepiece) 模型是 protobuf 格式。
 * 我们不需要它的训练/编码能力，只需要：
 *  - 把字符串拆成 piece id（用于 ONNX 输入）
 *  - 把 piece id 合并回字符串（用于 ONNX 输出解码）
 *
 * 这里实现读取 .vocab / .model 文件中"piece -> id"映射的两种常见格式：
 *  - 简单 vocab：每行 "piece<TAB>id"（适用于导出脚本生成的 vocab）
 *  - 完整 SPM .model：protobuf 二进制，我们读取其中的 pieces 表（Unigram）
 *
 *  注意：完整 SPM 解码比较复杂（需要 segment-score），本实现做了如下取舍：
 *    1. 对于 MarianMT 等"单 piece 词表"模型（piece 通常以 ▁ 开头/结尾的整词），
 *       使用简单的"最长前缀匹配"即可得到接近的解码。
 *    2. 完整实现推荐使用 jni 集成 libsentencepiece；为了 APK 体积，本应用选择纯 Kotlin 方案。
 *
 *  该实现可工作于 OPUS-MT 系列的 ONNX 导出模型（其 vocab 是高频子词粒度）。
 */
class SentencePieceLite {

    private val pieces: Array<String>
    private val scores: FloatArray
    /** piece -> id */
    private val pieceToId: HashMap<String, Int>

    constructor(pieces: List<String>, scores: FloatArray) {
        this.pieces = pieces.toTypedArray()
        this.scores = scores
        this.pieceToId = HashMap<String, Int>(pieces.size * 2).apply {
            for ((i, p) in pieces.withIndex()) put(p, i)
        }
    }

    /**
     * 加载 .vocab 文件（每行 "piece<TAB>score"，piece 是字符串字面量，▁ 用空格代替）。
     */
    constructor(vocabFile: File) : this(
        parseVocabLines(vocabFile.readLines(StandardCharsets.UTF_8))
    )

    private constructor(pieceScorePairs: List<Pair<String, Float>>) : this(
        pieceScorePairs.map { it.first },
        pieceScorePairs.map { it.second }.toFloatArray()
    )

    companion object {
        /** 通用 SPM token：句首（用于 decoder BOS） */
        const val BOS_TOKEN = "<s>"
        const val EOS_TOKEN = "</s>"
        const val PAD_TOKEN = "<pad>"
        const val UNK_TOKEN = "<unk>"
        const val SEP_TOKEN = "</s>"
        private const val SPACE_MARKER_CHAR = '\u2581'
        private const val SPACE_MARKER = "\u2581"

        fun parseVocabLines(lines: List<String>): List<Pair<String, Float>> =
            lines.mapNotNull { line ->
                val s = line.trim()
                if (s.isEmpty() || s.startsWith("#")) return@mapNotNull null
                val parts = s.split('\t', ' ', limit = 2)
                if (parts.size < 2) return@mapNotNull null
                val piece = parts[0]
                val score = parts[1].toFloatOrNull() ?: 0f
                piece to score
            }

        /**
         * 简化 SPM .model 读取：只取 pieces 字段。
         * 真实 SPM .model 是 protobuf，字段 1 = pieces (repeated Message)。
         *
         * 这里做一个"近似"：跳过 protobuf 字段头，只读可打印 ASCII 字符串。
         * 对于 MarianMT 模型的 SPM 文件，pieces 通常以 \x0a 开头（field 1, wire type 2），
         * 每个 piece Message 包含 piece (field 1)、score (field 2)。
         */
        fun loadFromSpmModel(file: File): SentencePieceLite {
            val bytes = file.readBytes()
            val pieces = mutableListOf<String>()
            val scores = mutableListOf<Float>()
            var pos = 0

            // SentencePiece ModelProto stores each piece as repeated field 1 (length-delimited).
            while (pos < bytes.size) {
                val tag = readVarint(bytes, pos)
                pos = tag.second
                val field = (tag.first ushr 3).toInt()
                val wire = (tag.first and 7L).toInt()
                if (field == 1 && wire == 2) {
                    val lengthResult = readVarint(bytes, pos)
                    val length = lengthResult.first.toInt()
                    pos = lengthResult.second
                    if (length < 0 || pos + length > bytes.size) break
                    val pieceBytes = bytes.copyOfRange(pos, pos + length)
                    pos += length
                    val parsed = parsePiece(pieceBytes)
                    if (parsed.first.isNotEmpty()) {
                        pieces += parsed.first
                        scores += parsed.second
                    }
                } else {
                    pos = skipField(bytes, pos, wire)
                }
            }

            require(pieces.isNotEmpty()) { "无法解析 SentencePiece 模型：${file.name}" }
            return SentencePieceLite(pieces, scores.toFloatArray())
        }

        private fun parsePiece(bytes: ByteArray): Pair<String, Float> {
            var pos = 0
            var piece = ""
            var score = 0f
            while (pos < bytes.size) {
                val tag = readVarint(bytes, pos)
                pos = tag.second
                val field = (tag.first ushr 3).toInt()
                val wire = (tag.first and 7L).toInt()
                when {
                    field == 1 && wire == 2 -> {
                        val lengthResult = readVarint(bytes, pos)
                        val length = lengthResult.first.toInt()
                        pos = lengthResult.second
                        if (length < 0 || pos + length > bytes.size) break
                        piece = String(bytes, pos, length, StandardCharsets.UTF_8)
                        pos += length
                    }
                    field == 2 && wire == 5 -> {
                        if (pos + 4 > bytes.size) break
                        val bits = (bytes[pos].toInt() and 0xff) or
                            ((bytes[pos + 1].toInt() and 0xff) shl 8) or
                            ((bytes[pos + 2].toInt() and 0xff) shl 16) or
                            ((bytes[pos + 3].toInt() and 0xff) shl 24)
                        score = java.lang.Float.intBitsToFloat(bits)
                        pos += 4
                    }
                    else -> pos = skipField(bytes, pos, wire)
                }
            }
            return piece to score
        }

        private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
            var pos = start
            var result = 0L
            var shift = 0
            while (pos < bytes.size && shift < 64) {
                val b = bytes[pos++].toInt() and 0xff
                result = result or ((b and 0x7f).toLong() shl shift)
                if ((b and 0x80) == 0) return result to pos
                shift += 7
            }
            return result to pos
        }

        private fun skipField(bytes: ByteArray, start: Int, wire: Int): Int {
            return when (wire) {
                0 -> readVarint(bytes, start).second
                1 -> (start + 8).coerceAtMost(bytes.size)
                2 -> {
                    val length = readVarint(bytes, start)
                    (length.second + length.first.toInt()).coerceAtMost(bytes.size)
                }
                5 -> (start + 4).coerceAtMost(bytes.size)
                else -> bytes.size
            }
        }
    }

    /**
     * 编码：把句子转成 piece id 序列。
     *  - 句首加 BOS；句尾加 EOS。
     *  - 子词切分采用"最大公共前缀"启发式（仅适用于字典化 OPUS-MT vocab）。
     */
    fun encode(text: String, maxLength: Int = 256): IntArray {
        require(maxLength >= 2) { "maxLength 至少为 2" }
        val normalized = SPACE_MARKER + text.trim().replace(' ', SPACE_MARKER_CHAR)
        if (normalized.isEmpty()) return intArrayOf(idOrUnk(EOS_TOKEN))

        // Viterbi segmentation: choose the highest-scoring complete piece sequence,
        // rather than greedily taking the longest prefix (which can produce invalid token IDs).
        val n = normalized.length
        val bestScore = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val nextPosition = IntArray(n + 1) { -1 }
        val nextPiece = IntArray(n + 1) { idOrUnk(UNK_TOKEN) }
        bestScore[n] = 0f
        val maxPieceLength = 64

        for (i in n - 1 downTo 0) {
            val limit = minOf(maxPieceLength, n - i)
            for (length in 1..limit) {
                val j = i + length
                val id = pieceToId[normalized.substring(i, j)] ?: continue
                if (id < 0 || id >= scores.size || bestScore[j].isInfinite() && j != n) continue
                val candidate = scores[id] + bestScore[j]
                if (candidate > bestScore[i]) {
                    bestScore[i] = candidate
                    nextPosition[i] = j
                    nextPiece[i] = id
                }
            }

            // Unknown fallback advances one UTF-16 code unit; normal Chinese/Russian/English
            // characters are covered, and the guard prevents a dead-end path for unseen symbols.
            if (nextPosition[i] < 0) {
                nextPosition[i] = i + 1
                nextPiece[i] = idOrUnk(UNK_TOKEN)
                bestScore[i] = -100f + bestScore[i + 1].takeIf { it.isFinite() } .orZero()
            }
        }

        val out = ArrayList<Int>(minOf(maxLength, n + 1))
        var position = 0
        while (position < n && out.size < maxLength - 1) {
            val next = nextPosition[position]
            if (next <= position || next > n) {
                out += idOrUnk(UNK_TOKEN)
                position++
            } else {
                out += nextPiece[position]
                position = next
            }
        }
        out += idOrUnk(EOS_TOKEN)
        return out.toIntArray()
    }

    private fun Float.orZero(): Float = if (isFinite()) this else 0f

    /**
     * 解码：把 piece id 合并成字符串。
     *  - 移除 BOS/EOS/PAD。
     *  - 把 ▁ 替换为空格。
     */
    fun decode(ids: IntArray): String {
        val sb = StringBuilder()
        for (id in ids) {
            if (id < 0 || id >= pieces.size) continue
            val p = pieces[id]
            if (p == BOS_TOKEN || p == EOS_TOKEN || p == PAD_TOKEN || p == UNK_TOKEN) continue
            sb.append(p.replace(SPACE_MARKER_CHAR, ' '))
        }
        return sb.toString().trim()
    }

    fun idOrUnk(piece: String): Int = pieceToId[piece] ?: pieceToId[UNK_TOKEN] ?: 0

    fun pieceFor(id: Int): String? = pieces.getOrNull(id)

    fun vocabSize(): Int = pieces.size

}
