package com.example.offlinetranslator.engine.asr

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * 麦克风 PCM 录音器。
 *
 *  输出：
 *  - 16 kHz / 16-bit mono PCM
 *  - File 形式（写入 wav 头）
 *  - 或 FloatArray（实时流式）
 */
class AudioRecorder(private val context: Context) {

    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @Volatile private var recording: Boolean = false
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null

    val pcmFlow: MutableSharedFlow<FloatArray> = MutableSharedFlow(extraBufferCapacity = 16)

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (recording) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO 权限未授予")
            return false
        }
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuf <= 0) {
            Log.e(TAG, "AudioRecord 不支持: minBuf=$minBuf")
            return false
        }
        val bufSize = maxOf(minBuf * 2, 4096)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, channelConfig, audioFormat, bufSize)
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord 初始化失败")
            return false
        }
        audioRecord = rec
        recording = true
        rec.startRecording()

        recordThread = Thread {
            val buffer = ShortArray(bufSize / 2)
            while (recording) {
                val read = rec.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val floats = FloatArray(read)
                    for (i in 0 until read) floats[i] = buffer[i] / 32768f
                    pcmFlow.tryEmit(floats)
                }
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            name = "AudioRecorder"
            start()
        }
        return true
    }

    fun stop() {
        if (!recording) return
        recording = false
        try {
            audioRecord?.stop()
        } catch (_: Throwable) {}
        audioRecord?.release()
        audioRecord = null
        recordThread?.join(500)
        recordThread = null
    }

    /**
     * 一次性录制到 wav 文件（带 wav 头）。
     *  返回录制的 PCM 样本数。
     */
    suspend fun recordToFile(target: File, maxDurationMs: Int = 30_000): Int = withContext(Dispatchers.IO) {
        if (!start()) return@withContext -1
        try {
            val out = FileOutputStream(target)
            // 写占位 wav 头（44 字节），结束时回填
            val header = ByteArray(44)
            out.write(header)

            val buffer = ShortArray(2048)
            var totalSamples = 0
            val maxSamples = sampleRate * maxDurationMs / 1000

            while (totalSamples < maxSamples && recording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read <= 0) continue
                // 写入 PCM（little endian）
                for (i in 0 until read) {
                    val s = buffer[i].toInt()
                    out.write(s and 0xff)
                    out.write((s shr 8) and 0xff)
                }
                totalSamples += read
            }

            // 回填 wav 头
            val totalBytes = totalSamples * 2
            val headerBytes = wavHeader(totalBytes, sampleRate, 1, 16)
            out.seek(0)
            out.write(headerBytes)
            out.close()
            totalSamples
        } finally {
            stop()
        }
    }

    private fun wavHeader(dataSize: Int, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val totalSize = 36 + dataSize
        return byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            (totalSize and 0xff).toByte(),
            ((totalSize shr 8) and 0xff).toByte(),
            ((totalSize shr 16) and 0xff).toByte(),
            ((totalSize shr 24) and 0xff).toByte(),
            'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
            'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(),
            16, 0, 0, 0, // PCM chunk size
            1, 0, // PCM
            channels.toByte(), 0,
            (sampleRate and 0xff).toByte(),
            ((sampleRate shr 8) and 0xff).toByte(),
            ((sampleRate shr 16) and 0xff).toByte(),
            ((sampleRate shr 24) and 0xff).toByte(),
            (byteRate and 0xff).toByte(),
            ((byteRate shr 8) and 0xff).toByte(),
            ((byteRate shr 16) and 0xff).toByte(),
            ((byteRate shr 24) and 0xff).toByte(),
            (channels * bitsPerSample / 8).toByte(), 0,
            bitsPerSample.toByte(), 0,
            'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(),
            (dataSize and 0xff).toByte(),
            ((dataSize shr 8) and 0xff).toByte(),
            ((dataSize shr 16) and 0xff).toByte(),
            ((dataSize shr 24) and 0xff).toByte(),
        )
    }

    companion object {
        private const val TAG = "AudioRecorder"

        /** 简易 wav 头 */
        fun readWavAsFloat(path: String): Pair<FloatArray, Int>? {
            return try {
                val file = File(path)
                val bytes = file.readBytes()
                if (bytes.size < 44 || String(bytes.copyOfRange(0, 4)) != "RIFF") return null
                val sampleRate = bytes.copyOfRange(24, 28).let { b ->
                    (b[0].toInt() and 0xff) or
                        ((b[1].toInt() and 0xff) shl 8) or
                        ((b[2].toInt() and 0xff) shl 16) or
                        ((b[3].toInt() and 0xff) shl 24)
                }
                val bitsPerSample = bytes[34].toInt() and 0xff
                val channels = bytes[22].toInt() and 0xff
                require(channels == 1 && bitsPerSample == 16) { "仅支持 16-bit mono wav" }
                val pcm = bytes.copyOfRange(44, bytes.size)
                val samples = FloatArray(pcm.size / 2)
                for (i in samples.indices) {
                    val lo = pcm[i * 2].toInt() and 0xff
                    val hi = pcm[i * 2 + 1].toInt()
                    samples[i] = ((hi shl 8) or lo).toShort().toFloat() / 32768f
                }
                samples to sampleRate
            } catch (t: Throwable) {
                null
            }
        }
    }
}
