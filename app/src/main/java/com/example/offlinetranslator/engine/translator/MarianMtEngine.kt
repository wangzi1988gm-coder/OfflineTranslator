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
            val onnxRoot = File(root, "onnx").takeIf { it.isDirectory } ?: root
            val encoder = listOf(
                File(root, "encoder.onnx"), File(root, "encoder_model.onnx"),
                File(onnxRoot, "encoder_model_int8.onnx"), File(onnxRoot, "encoder_model.onnx"),
            ).firstOrNull { it.isFile } ?: File(root, "encoder_model.onnx")
            val decoder = listOf(
                File(root, "decoder.onnx"), File(root, "decoder_model.onnx"),
                File(onnxRoot, "decoder_model_merged_int8.onnx"), File(onnxRoot, "decoder_model_merged.onnx"),
                File(onnxRoot, "decoder_model.onnx"),
            ).firstOrNull { it.isFile } ?: File(root, "decoder_model.onnx")
            val decoderLm = File(root, "decoder_with_lm_head.onnx")
            val srcSpmFile = File(root, "source.spm")
            val tgtSpmFile = File(root, "target.spm")
            val vocabSrc = File(root, "vocab.src")
            val vocabTgt = File(root, "vocab.tgt")

            require(encoder.isFile) { "缺少 encoder.onnx / encoder_model.onnx（也检查了 onnx/ 子目录）" }
            require(decoder.isFile || decoderLm.isFile) { "缺少 decoder*.onnx（也检查了 onnx/ 子目录）" }

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
                ?: 0

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

            val env = OrtEnvironment.getEnvironment()
            val srcIds = srcSpm!!.encode(text, maxLength)
            val srcTensor = OnnxTensor.createTensor(
                env, LongBuffer.wrap(srcIds.map { it.toLong() }.toLongArray()),
                longArrayOf(1, srcIds.size.toLong()),
            )
            val srcMaskTensor = OnnxTensor.createTensor(
                env, LongBuffer.wrap(LongArray(srcIds.size) { 1L }),
                longArrayOf(1, srcIds.size.toLong()),
            )
            var encOut: OrtSession.Result? = null
            var hiddenTensor: OnnxTensor? = null
            var previousResult: OrtSession.Result? = null
            val emptyCaches = mutableListOf<OnnxTensor>()
            try {
                val encInputs = hashMapOf(
                    "input_ids" to srcTensor,
                    "attention_mask" to srcMaskTensor,
                )
                encOut = session!!.run(encInputs)
                val hiddenStates = encOut[0].value as Array<Array<FloatArray>>
                hiddenTensor = OnnxTensor.createTensor(env, hiddenStates)

                val decoder = decoderSession!!
                val inputNames = decoder.inputInfo.keys
                val outputNames = decoder.outputInfo.keys
                val cacheInputNames = inputNames.filter { it.startsWith("past_key_values.") }
                val mergedDecoder = inputNames.contains("use_cache_branch")

                // The merged Optimum decoder requires empty past-key-value tensors on its first pass.
                if (mergedDecoder) {
                    for (name in cacheInputNames) {
                        val info = decoder.inputInfo[name]?.info as? ai.onnxruntime.TensorInfo
                            ?: continue
                        val shape = info.shape.copyOf()
                        for (i in shape.indices) if (shape[i] < 0) shape[i] = 1L
                        if (shape.isNotEmpty()) shape[shape.lastIndex - 1] = 0L
                        val count = shape.fold(1L) { acc, dim -> acc * dim }.coerceAtLeast(0L)
                        val tensor = OnnxTensor.createTensor(
                            env, java.nio.FloatBuffer.wrap(FloatArray(count.toInt())), shape
                        )
                        emptyCaches += tensor
                    }
                }

                val generated = ArrayList<Int>(maxLength)
                generated += decoderStartId
                var nextInputId = decoderStartId
                for (step in 0 until maxLength - 1) {
                    val inputIds = OnnxTensor.createTensor(
                        env, LongBuffer.wrap(longArrayOf(nextInputId.toLong())), longArrayOf(1, 1)
                    )
                    val branchTensor = if (mergedDecoder) {
                        OnnxTensor.createTensor(env, booleanArrayOf(step > 0))
                    } else null
                    val inputs = HashMap<String, OnnxTensor>()
                    inputs["input_ids"] = inputIds
                    inputs["encoder_hidden_states"] = hiddenTensor!!
                    inputs["encoder_attention_mask"] = srcMaskTensor

                    if (mergedDecoder) {
                        inputs["use_cache_branch"] = branchTensor!!
                        if (step == 0) {
                            cacheInputNames.forEachIndexed { index, name ->
                                if (index < emptyCaches.size) inputs[name] = emptyCaches[index]
                            }
                        } else {
                            val prior = previousResult
                                ?: throw IllegalStateException("解码器 KV cache 丢失")
                            cacheInputNames.forEach { name ->
                                val outputName = "present." + name.removePrefix("past_key_values.")
                                val actualName = when {
                                    outputNames.contains(outputName) -> outputName
                                    outputNames.contains(name.replace("past_key_values.", "present.")) ->
                                        name.replace("past_key_values.", "present.")
                                    else -> throw IllegalStateException("解码器缺少缓存输出：$outputName")
                                }
                                inputs[name] = prior.get(actualName).orElseThrow {
                                    IllegalStateException("无法读取解码器缓存：$actualName")
                                } as OnnxTensor
                            }
                        }
                    }

                    val result = try {
                        decoder.run(inputs)
                    } finally {
                        inputIds.close()
                        branchTensor?.close()
                    }
                    if (step == 0) emptyCaches.forEach { runCatching { it.close() } }
                    // New outputs are available before the previous result is closed.
                    if (step > 0) runCatching { previousResult?.close() }
                    previousResult = null

                    val logitsValue = if (outputNames.contains("logits")) {
                        result.get("logits").orElse(result[0])
                    } else result[0]
                    val logits = logitsValue.value as Array<Array<FloatArray>>
                    val nextId = argmax(logits[0][logits[0].lastIndex])
                    if (nextId == eosId) {
                        result.close()
                        break
                    }
                    generated += nextId
                    nextInputId = nextId
                    previousResult = result
                }

                val translated = tgtSpm!!.decode(generated.drop(1).toIntArray())
                TranslationResult(
                    translatedText = translated,
                    engineId = engineId,
                    elapsedMillis = System.currentTimeMillis() - start,
                )
            } finally {
                runCatching { previousResult?.close() }
                emptyCaches.forEach { runCatching { it.close() } }
                runCatching { hiddenTensor?.close() }
                runCatching { encOut?.close() }
                runCatching { srcTensor.close() }
                runCatching { srcMaskTensor.close() }
            }
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
