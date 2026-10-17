package com.example.offlinetranslator.engine.asr

import android.content.Context
import android.util.Log
import com.example.offlinetranslator.data.Language
import com.example.offlinetranslator.data.ModelCategory
import com.example.offlinetranslator.data.ModelPack
import com.example.offlinetranslator.model.ModelStorage
import com.example.offlinetranslator.model.ModelManager
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 基于 sherpa-onnx 的离线 ASR 引擎。
 *
 *  支持的模型形态：
 *   - Transducer（encoder.onnx + decoder.onnx + joiner.onnx + tokens.txt）
 *   - Paraformer（单 model.onnx + tokens.txt）
 *   - Whisper（encoder.onnx + decoder.onnx + tokens.txt）
 *
 *  模型路径：
 *   /Android/data/<pkg>/files/models/asr/<lang>/
 */
class SherpaOnnxAsrEngine(
    private val context: Context,
    private val storage: ModelStorage,
) : AsrEngine {

    override val engineId = "SHERPA_ONNX"

    private val recognizers = HashMap<Language, OnlineRecognizer>()

    fun load(language: Language, pack: ModelPack?): Boolean {
        if (pack == null || pack.category != ModelCategory.ASR) return false
        return try {
            val root = File(storage.modelsRoot, pack.rootDir)
            if (!root.exists()) {
                Log.w(TAG, "ASR 模型目录不存在: ${root.absolutePath}")
                return false
            }
            val tokensFile = File(root, "tokens.txt")
            if (!tokensFile.exists()) {
                Log.w(TAG, "ASR 缺少 tokens.txt: ${root.absolutePath}")
                return false
            }

            val encoder = File(root, "encoder.onnx").takeIf { it.exists() }
            val decoder = File(root, "decoder.onnx").takeIf { it.exists() }
            val joiner = File(root, "joiner.onnx").takeIf { it.exists() }
            val isTransducer = encoder != null && decoder != null && joiner != null
            val isParaformer = encoder != null && decoder != null && joiner == null
            if (!isTransducer && !isParaformer) {
                Log.e(TAG, "未识别出支持的在线 sherpa-onnx 模型（Transducer 或 Paraformer encoder/decoder）")
                return false
            }

            val modelConfig = OnlineModelConfig(
                transducer = if (isTransducer) {
                    OnlineTransducerModelConfig(
                        encoder = encoder!!.absolutePath,
                        decoder = decoder!!.absolutePath,
                        joiner = joiner!!.absolutePath,
                    )
                } else OnlineTransducerModelConfig(),
                paraformer = if (isParaformer) {
                    OnlineParaformerModelConfig(
                        encoder = encoder!!.absolutePath,
                        decoder = decoder!!.absolutePath,
                    )
                } else OnlineParaformerModelConfig(),
                tokens = tokensFile.absolutePath,
                numThreads = 4,
                modelType = if (isTransducer) "transducer" else "paraformer",
            )
            val recognizerConfig = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                modelConfig = modelConfig,
            )
            val recognizer = OnlineRecognizer(config = recognizerConfig)

            recognizers[language]?.release()
            recognizers[language] = recognizer
            Log.i(TAG, "ASR 加载成功: $language (${pack.rootDir})")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "ASR 加载失败: ${t.message}", t)
            false
        }
    }

    override fun isReady(language: Language): Boolean = recognizers[language] != null

    override suspend fun recognizeFile(audioPath: String, language: Language): String =
        withContext(Dispatchers.IO) {
            val (samples, sr) = AudioRecorder.readWavAsFloat(audioPath) ?: return@withContext ""
            if (sr != 16_000) {
                Log.w(TAG, "采样率非 16 kHz（$sr），重采样跳过")
            }
            recognizeSamples(samples, sr, language)
        }

    override suspend fun recognizeSamples(samples: FloatArray, sampleRate: Int, language: Language): String =
        withContext(Dispatchers.Default) {
            val recognizer = recognizers[language]
                ?: throw AsrNotReadyException("ASR 未就绪: $language")
            val stream = recognizer.createStream()
            val chunkSize = 4096
            var pos = 0
            val sb = StringBuilder()
            while (pos < samples.size) {
                val end = minOf(pos + chunkSize, samples.size)
                val chunk = samples.copyOfRange(pos, end)
                stream.acceptWaveform(chunk, sampleRate)
                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }
                val r = recognizer.getResult(stream)
                if (r.text.isNotBlank()) {
                    sb.clear()
                    sb.append(r.text)
                }
                if (recognizer.isEndpoint(stream)) {
                    recognizer.reset(stream)
                }
                pos = end
            }
            stream.release()
            sb.toString().trim()
        }

    override fun createStreaming(language: Language): StreamingAsr {
        val recognizer = recognizers[language]
            ?: throw AsrNotReadyException("ASR 未就绪: $language")
        return SherpaStreaming(recognizer)
    }

    private class SherpaStreaming(private val recognizer: OnlineRecognizer) : StreamingAsr {
        private val stream: OnlineStream = recognizer.createStream()
        private val sb = StringBuilder()

        override fun acceptWaveform(samples: FloatArray, sampleRate: Int) {
            stream.acceptWaveform(samples, sampleRate)
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }
            sb.clear()
            sb.append(recognizer.getResult(stream).text)
        }

        override fun decodeStep(): String = sb.toString()

        override fun finalize(): String {
            stream.acceptWaveform(FloatArray(8000) { 0f }, 16_000)
            while (recognizer.isReady(stream)) recognizer.decode(stream)
            return recognizer.getResult(stream).text.trim()
        }

        override fun reset() {
            recognizer.reset(stream)
            sb.clear()
        }

        override fun release() {
            stream.release()
        }
    }

    fun releaseAll() {
        recognizers.values.forEach { it.release() }
        recognizers.clear()
    }

    companion object {
        private const val TAG = "SherpaOnnxAsrEngine"
    }
}
