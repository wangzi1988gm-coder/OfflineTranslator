package com.example.offlinetranslator.engine.translator

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import com.example.offlinetranslator.data.Language
import com.example.offlinetranslator.data.ModelCategory
import com.example.offlinetranslator.data.ModelPack
import com.example.offlinetranslator.engine.translator.tokenize.SentencePieceLite
import com.example.offlinetranslator.model.ModelStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer

/**
 * MarianMT 离线翻译引擎（ONNX Runtime Mobile）。
 *
 * 加载方式：
 *  /Android/data/<pkg>/files/models/translator/<slotKey>/
 *      ├── encoder.onnx
 *      ├── decoder.onnx         （可选；自回归）
 *      ├── decoder_with_lm_head.onnx  （二选一）
 *      ├── source.spm           源语言 SPM .model
 *      ├── target.spm           目标语言 SPM .model
 *      └── config.json          {"max_length":128, "vocab_size":...}
 *
 * 推理流程（自回归）：
 *  1. encode source -> int[]
 *  2. encoder(input_ids) -> encoder_hidden_states
 *  3. 循环：
 *     decoder(input_ids=tgt_ids, encoder_hidden_states=hidden, attention_mask=src_mask)
 *       -> logits
 *     argmax(logits[-1]) -> next token id
 *     遇 EOS 或达到 max_length 结束
 *  4. decode target_ids -> string
 */
class MarianMtEngine(
    private val storage: ModelStorage,
) : TranslationEngine {

    override val engineId: String = "MARIAN_MT"
    private var session: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var srcSpm: SentencePieceLite? = null
    private var tgtSpm: SentencePieceLite? = null
    private var slotKey: String? = null
    private var maxLength: Int = 128
    private var decoderStartId: Int = 65000
    private var eosId: Int = 0

    @Volatile
    private var loaded: Boolean = false

    override val isReady: Boolean get() = loaded

    fun load(pack: ModelPack): Boolean {
        if (pack.category != ModelCategory.TRANSLATOR) return false
        return try {
            unload()
            val root = File(storage.modelsRoot, pack.rootDir)
            val encoder = File(root, "encoder.onnx").takeIf { it.isFile }
                ?: File(root, "encoder_model.onnx")
            val decoder = File(root, "decoder.onnx").takeIf { it.isFile }
                ?: File(root, "decoder_model.onnx")
            val decoderLm = File(root, "decoder_with_lm_head.onnx")
            val srcSpmFile = File(root, "source.spm")
            val tgtSpmFile = File(root, "target.spm")
            val vocabSrc = File(root, "vocab.src")
            val vocabTgt = File(root, "vocab.tgt")

            require(encoder.isFile) { "缺少 encoder.onnx / encoder_model.onnx" }
            require(decoder.isFile || decoderLm.isFile) { "缺少 decoder*.onnx" }

            // OPUS-MT Marian exports usually use PAD (65000) as decoder start and EOS (0).
            val configText = File(root, "config.json").takeIf { it.isFile }?.readText().orEmpty()
            decoderStartId = Regex("\\\"decoder_start_token_id\\\"\\s*:\\s*(\\d+)")
                .find(configText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 65000
            eosId = Regex("\\\"eos_token_id\\\"\\s*:\\s*(\\d+)")
                .find(configText)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: tgtSpm?.idOrUnk(SentencePieceLite.EOS_TOKEN) ?: 0

            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                // 尝试 NNAPI；不同 ONNX Runtime 版本 API 不一致，用反射
                runCatching {
                    val method = this::class.java.methods.firstOrNull { it.name == "addNnapi" }
                    method?.invoke(this)
                }.onFailure { Log.w(TAG, "NNAPI 不可用，使用 CPU: ${it.message}") }
                setIntraOpNumThreads(4)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            session = env.createSession(encoder.absolutePath, opts)
            val decoderFile = when {
                decoderLm.exists() -> decoderLm
                decoder.exists() -> decoder
                else -> null
            }
            require(decoderFile != null) { "缺少 decoder*.onnx" }
            decoderSession = env.createSession(decoderFile.absolutePath, opts)

            srcSpm = if (srcSpmFile.exists()) {
                SentencePieceLite.loadFromSpmModel(srcSpmFile)
            } else if (vocabSrc.exists()) {
                SentencePieceLite(vocabSrc)
            } else null

            tgtSpm = if (tgtSpmFile.exists()) {
                SentencePieceLite.loadFromSpmModel(tgtSpmFile)
            } else if (vocabTgt.exists()) {
                SentencePieceLite(vocabTgt)
            } else null

            require(srcSpm != null && tgtSpm != null) { "缺少 SPM / vocab 文件" }
            eosId = Regex("\\\"eos_token_id\\\"\\s*:\\s*(\\d+)")
                .find(configText)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: tgtSpm!!.idOrUnk(SentencePieceLite.EOS_TOKEN)

            slotKey = pack.slotKey
            loaded = true
            true
        } catch (t: Throwable) {
            Log.e(TAG, "load failed: ${t.message}", t)
            unload()
            false
        }
    }

    fun unload() {
        runCatching { session?.close() }
        runCatching { decoderSession?.close() }
        session = null
        decoderSession = null
        srcSpm = null
        tgtSpm = null
        slotKey = null
        loaded = false
    }

    override suspend fun translate(text: String, src: Language, tgt: Language): TranslationResult =
        withContext(Dispatchers.Default) {
            val start = System.currentTimeMillis()
            if (!loaded || session == null || decoderSession == null) {
                throw EngineNotReadyException("MarianMt 未加载。$slotKey")
            }
            val srcIds = srcSpm!!.encode(text, maxLength)
            val srcTensor = OnnxTensor.createTensor(
                OrtEnvironment.getEnvironment(),
                LongBuffer.wrap(srcIds.map { it.toLong() }.toLongArray()),
                longArrayOf(1, srcIds.size.toLong()),
            )
            val srcMask = LongArray(srcIds.size) { 1L }
            val srcMaskTensor = OnnxTensor.createTensor(
                OrtEnvironment.getEnvironment(),
                LongBuffer.wrap(srcMask),
                longArrayOf(1, srcIds.size.toLong()),
            )

            // Encoder 前向：input_ids + attention_mask
            val encInputs = HashMap<String, OnnxTensor>()
            encInputs["input_ids"] = srcTensor
            encInputs["attention_mask"] = srcMaskTensor
            val encOut = session!!.run(encInputs)
            val hiddenStates = encOut[0].value as Array<Array<FloatArray>>  // [1, seq, hidden]

            // 自回归生成（简化版：每步构造 decoder 输入）
            val tgtIds = ArrayList<Int>(maxLength)
            tgtIds += tgtSpm!!.idOrUnk(SentencePieceLite.BOS_TOKEN)
            val eosId = tgtSpm!!.idOrUnk(SentencePieceLite.EOS_TOKEN)

            val decEnv = OrtEnvironment.getEnvironment()
            val hiddenTensor = OnnxTensor.createTensor(decEnv, hiddenStates)
            var lastTokens: IntArray = tgtIds.toIntArray()
            for (step in 0 until maxLength - 1) {
                val tgtArr = LongArray(lastTokens.size) { lastTokens[it].toLong() }
                val tgtTensor = OnnxTensor.createTensor(
                    decEnv, LongBuffer.wrap(tgtArr), longArrayOf(1, tgtArr.size.toLong())
                )
                val inputs = HashMap<String, OnnxTensor>()
                inputs["input_ids"] = tgtTensor
                inputs["encoder_hidden_states"] = hiddenTensor
                inputs["encoder_attention_mask"] = srcMaskTensor
                val logitsOut = decoderSession!!.run(inputs)
                val logits = logitsOut[0].value as Array<Array<FloatArray>>
                val lastStep = logits[0][logits[0].size - 1]
                val nextId = argmax(lastStep)
                runCatching { logitsOut.close() }
                runCatching { tgtTensor.close() }
                if (nextId == eosId) break
                tgtIds += nextId
                lastTokens = tgtIds.toIntArray()
            }

            val translated = tgtSpm!!.decode(tgtIds.toIntArray())

            runCatching { srcTensor.close() }
            runCatching { srcMaskTensor.close() }
            runCatching { encOut.close() }
            runCatching { hiddenTensor.close() }

            TranslationResult(
                translatedText = translated,
                engineId = engineId,
                elapsedMillis = System.currentTimeMillis() - start,
            )
        }

    private fun argmax(arr: FloatArray): Int {
        var bestIdx = 0
        var bestVal = arr[0]
        for (i in 1 until arr.size) {
            if (arr[i] > bestVal) { bestVal = arr[i]; bestIdx = i }
        }
        return bestIdx
    }

    companion object {
        private const val TAG = "MarianMtEngine"
    }
}
