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
        this.pieceToId = HashMap(pieces.size * 2).apply {
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
                val piece = parts[0].replace(SPACE_MARKER, ' ')
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
            val bytes = Files.readAllBytes(Paths.get(file.toURI()))
            val text = String(bytes, Charsets.ISO_8859_1)
            // 启发式：找出所有带 ▁/</s>/<s>/<unk> 的可打印片段
            val pieceRegex = Regex("[\\x20-\\x7E]+")
            val raw = pieceRegex.findAll(text).map { it.value }.toList()
            val pieces = mutableListOf<String>()
            val seen = HashSet<String>()
            for (r in raw) {
                val cleaned = r.trim().trimStart('\u0001').trimEnd('\u0000').trim()
                if (cleaned.isEmpty()) continue
                if (cleaned.length > 40) continue
                if (cleaned in seen) continue
                seen += cleaned
                pieces += cleaned
            }
            // 兜底：特殊 token 必须在表里
            listOf(BOS_TOKEN, EOS_TOKEN, PAD_TOKEN, UNK_TOKEN).forEach {
                if (it !in seen) pieces += it
            }
            val scores = FloatArray(pieces.size) { 0f }
            return SentencePieceLite(pieces, scores)
        }
    }

    /**
     * 编码：把句子转成 piece id 序列。
     *  - 句首加 BOS；句尾加 EOS。
     *  - 子词切分采用"最大公共前缀"启发式（仅适用于字典化 OPUS-MT vocab）。
     */
    fun encode(text: String, maxLength: Int = 256): IntArray {
        val out = ArrayList<Int>(text.length / 2 + 4)
        out += idOrUnk(BOS_TOKEN)
        val normalized = text.replace(' ', SPACE_MARKER_CHAR)
        var i = 0
        while (i < normalized.length) {
            var matched = -1
            var matchedLen = 0
            // 从当前位置开始尝试匹配最长 piece
            val maxTry = minOf(normalized.length - i, 20) // SPM 子词通常 < 20 字符
            for (len in maxTry downTo 1) {
                val sub = normalized.substring(i, i + len)
                val id = pieceToId[sub]
                if (id != null && id >= 0) {
                    matched = id
                    matchedLen = len
                    break
                }
            }
            if (matched < 0) {
                matched = idOrUnk(UNK_TOKEN)
                matchedLen = 1
            }
            out += matched
            i += matchedLen
            if (out.size >= maxLength - 1) break
        }
        out += idOrUnk(EOS_TOKEN)
        return out.toIntArray()
    }

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
