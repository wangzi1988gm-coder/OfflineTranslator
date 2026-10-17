package com.example.offlinetranslator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.offlinetranslator.data.AppSettings
import com.example.offlinetranslator.ui.nav.AppNav
import com.example.offlinetranslator.ui.screen.conversation.ConversationViewModel
import com.example.offlinetranslator.ui.screen.image.ImageTranslateViewModel
import com.example.offlinetranslator.ui.screen.models.ModelManageViewModel
import com.example.offlinetranslator.ui.screen.settings.SettingsViewModel
import com.example.offlinetranslator.ui.screen.translate.TranslateViewModel
import com.example.offlinetranslator.ui.theme.OfflineTranslatorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as App).container
        val historyDao = container.historyDao

        setContent {
            val themeMode = com.example.offlinetranslator.data.ThemeMode.FOLLOW_SYSTEM

            OfflineTranslatorTheme(themeMode = themeMode) {
                val translateVm: TranslateViewModel = factory {
                    TranslateViewModel(
                        translator = container.translatorRouter,
                        asr = container.asrRouter,
                        tts = container.ttsRouter,
                        ocr = container.ocrEngine,
                        historyDao = historyDao,
                        settingsProvider = { container.currentSettings() },
                        settingsSaver = { container.updateSettings(it) },
                    )
                }
                val conversationVm: ConversationViewModel = factory {
                    ConversationViewModel(
                        context = applicationContext,
                        asr = container.asrRouter,
                        translator = container.translatorRouter,
                        tts = container.ttsRouter,
                    )
                }
                val imageVm: ImageTranslateViewModel = factory {
                    ImageTranslateViewModel(
                        ocr = container.ocrEngine,
                        translator = container.translatorRouter,
                        tts = container.ttsRouter,
                    )
                }
                val modelVm: ModelManageViewModel = factory {
                    ModelManageViewModel(
                        modelStorage = container.modelStorage,
                        modelManager = container.modelManager,
                        translatorRouter = container.translatorRouter,
                    )
                }
                val settingsVm: SettingsViewModel = SettingsViewModel(container.currentSettings())

                AppNav(
                    translateVm = translateVm,
                    conversationVm = conversationVm,
                    imageVm = imageVm,
                    modelVm = modelVm,
                    settingsVm = settingsVm,
                )
            }
        }
    }

    private inline fun <reified VM : ViewModel> factory(crossinline create: () -> VM): VM {
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return create() as T
            }
        }
        return ViewModelProvider(this, factory)[VM::class.java]
    }
}
