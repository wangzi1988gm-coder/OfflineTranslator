package com.example.offlinetranslator.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.example.offlinetranslator.data.AppSettings
import com.example.offlinetranslator.data.ThemeMode
import com.example.offlinetranslator.data.TtsEnginePref
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(initial: AppSettings) : ViewModel() {
    private val _settings = MutableStateFlow(initial)
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setTtsEngine(p: TtsEnginePref) { _settings.value = _settings.value.copy(ttsEngine = p) }
    fun setTheme(m: ThemeMode) { _settings.value = _settings.value.copy(themeMode = m) }
    fun setUseVad(b: Boolean) { _settings.value = _settings.value.copy(useVad = b) }
    fun setOverlay(b: Boolean) { _settings.value = _settings.value.copy(overlayOnImage = b) }
}
