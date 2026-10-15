package com.example.offlinetranslator.engine.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.offlinetranslator.data.Language
import com.example.offlinetranslator.data.ModelCategory
import com.example.offlinetranslator.data.ModelPack
import com.example.offlinetranslator.model.ModelStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 基于 sherpa-onnx 离线 TTS（VITS / Piper / Kokoro）。
 *
 *  模型放置：
 *    /Android/data/<pkg>/files/models/tts/<lang>/
 *      ├── model.onnx       (VITS / Piper)
 *      ├── tokens.txt
 *      ├── espeak-ng-data/  (部分模型需要)
 *      └── lexicon.txt      (部分模型需要)
 *
 *  说明：
 *    - sherpa-onnx 的 Offline TTS API 视版本不同（构造参数、嵌套类路径），本文件提供一种实现骨架。
 *    - 用户在实际接入时，按 sherpa-onnx 实际 API 调整 OfflineTtsConfig 的构造。
 *    - 若运行时出现 ClassNotFound / NoSuchMethod，本引擎会回退到系统 TTS。
 */
class SherpaOnnxTtsEngine(
    private val context: Context,
    private val storage: ModelStorage,
) : TtsEngine {

    override val engineId = "SHERPA_ONNX_VITS"

    private val ttsMap = HashMap<Language, Any>()

    @Suppress("UNCHECKED_CAST")
    fun load(language: Language, pack: ModelPack?): Boolean {
        if (pack == null || pack.category != ModelCategory.TTS) return false
        return try {
            val root = File(storage.modelsRoot, pack.rootDir)
            val model = File(root, "model.onnx").takeIf { it.exists() }
                ?: root.listFiles()?.firstOrNull { it.name.endsWith(".onnx") }
                ?: return false
            val tokens = File(root, "tokens.txt")
            if (!tokens.exists()) return false

            // 通过反射调用避免对特定 API 的硬依赖
            val ttsClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTts")
            val cfgClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsConfig")
            val cfgInnerModel = Class.forName("com.k2fsa.sherpa.onnx.OfflineTts\$ModelConfig")
            val cfgInnerVits = Class.forName("com.k2fsa.sherpa.onnx.OfflineTts\$VitsModelConfig")

            val vitsConfig = cfgInnerVits.getDeclaredConstructor(
                String::class.java, String::class.java, String::class.java, String::class.java
            ).newInstance(model.absolutePath, tokens.absolutePath, "", "")

            val modelConfig = cfgInnerModel.getDeclaredConstructor(cfgInnerVits, Int::class.javaPrimitiveType)
                .newInstance(vitsConfig, 4)

            val cfg = cfgClass.getDeclaredConstructor(cfgInnerModel).newInstance(modelConfig)
            val tts = ttsClass.getDeclaredConstructor(cfgClass).newInstance(cfg)

            ttsMap[language]?.let { closeQuiet(it) }
            ttsMap[language] = tts
            true
        } catch (t: Throwable) {
            Log.e(TAG, "TTS load failed: ${t.message}", t)
            false
        }
    }

    private fun closeQuiet(tts: Any) {
        try {
            tts.javaClass.getMethod("close").invoke(tts)
        } catch (_: Throwable) {}
    }

    override fun isReady(language: Language): Boolean = ttsMap[language] != null

    @Suppress("UNCHECKED_CAST")
    override suspend fun speak(text: String, language: Language, rate: Float, pitch: Float): TtsResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val tts = ttsMap[language]
                ?: return@withContext TtsResult(false, engineId, language, warning = "离线 TTS 未加载: $language")
            if (text.isBlank()) return@withContext TtsResult(true, engineId, language)

            try {
                val generateMethod = tts.javaClass.getMethod(
                    "generate", String::class.java, Int::class.javaPrimitiveType, Float::class.javaPrimitiveType
                )
                val audio = generateMethod.invoke(tts, text, 0, rate)
                val samples = audio.javaClass.getMethod("getSamples").invoke(audio) as FloatArray
                val sampleRate = audio.javaClass.getMethod("getSampleRate").invoke(audio) as Int
                playPcm(samples, sampleRate)
                TtsResult(
                    spoken = true,
                    engineId = engineId,
                    language = language,
                    elapsedMillis = System.currentTimeMillis() - start,
                )
            } catch (t: Throwable) {
                TtsResult(false, engineId, language, warning = "推理失败: ${t.message}")
            }
        }

    private fun playPcm(samples: FloatArray, sampleRate: Int) {
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_FLOAT
        val bufferSize = maxOf(samples.size * 4, 4096)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(encoding)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        track.play()
        val durationMs = (samples.size * 1000L / sampleRate) + 100
        Thread.sleep(durationMs)
        track.stop()
        track.release()
    }

    override fun stop() {
        // 无 stop 接口
    }

    override fun release() {
        ttsMap.values.forEach { closeQuiet(it) }
        ttsMap.clear()
    }

    companion object {
        private const val TAG = "SherpaOnnxTtsEngine"
    }
}
