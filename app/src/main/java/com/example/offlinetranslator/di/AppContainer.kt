package com.example.offlinetranslator.di

import android.content.Context
import com.example.offlinetranslator.data.AppSettings
import com.example.offlinetranslator.data.HistoryDao
import com.example.offlinetranslator.data.SettingsRepository
import com.example.offlinetranslator.engine.asr.AsrRouter
import com.example.offlinetranslator.engine.ocr.MlKitOcrEngine
import com.example.offlinetranslator.engine.translator.MarianMtEngine
import com.example.offlinetranslator.engine.translator.TranslatorRouter
import com.example.offlinetranslator.engine.tts.TtsRouter
import com.example.offlinetranslator.model.ModelManager
import com.example.offlinetranslator.model.ModelStorage
import kotlinx.coroutines.runBlocking

/**
 * 简易 DI 容器。
 */
class AppContainer(private val context: Context, val historyDao: HistoryDao) {

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    val modelStorage: ModelStorage by lazy { ModelStorage(context) }
    val modelManager: ModelManager by lazy { ModelManager(context, modelStorage) }

    val translatorRouter: TranslatorRouter by lazy {
        TranslatorRouter(modelManager) { MarianMtEngine(modelStorage) }
    }

    val asrRouter: AsrRouter by lazy { AsrRouter(context, modelStorage, modelManager) }

    val ttsRouter: TtsRouter by lazy {
        TtsRouter(context, modelStorage, modelManager, settingsRepository.state.value.ttsEngine)
    }

    val ocrEngine: MlKitOcrEngine by lazy { MlKitOcrEngine(context) }

    init {
        // TTS 偏好同步
        settingsRepository.state
    }

    fun currentSettings(): AppSettings = settingsRepository.state.value

    fun updateSettings(s: AppSettings) {
        settingsRepository.update(s)
        ttsRouter.setPreference(s.ttsEngine)
    }

    fun preload() {
        // Scan persisted model folders before preloading engines; otherwise startup sees an empty inventory.
        runBlocking { modelManager.refresh() }
        translatorRouter.preloadAll()
        asrRouter.preload()
        ttsRouter.preload()
    }
}
