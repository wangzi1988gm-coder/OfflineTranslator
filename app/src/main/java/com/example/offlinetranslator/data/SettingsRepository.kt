package com.example.offlinetranslator.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 简化版设置仓库：SharedPreferences + StateFlow。
 *
 *  比 DataStore 更轻量，但只存 primitive + JSON 字符串。
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())

    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun load(): AppSettings {
        val text = prefs.getString(KEY_SETTINGS_JSON, null)
        return SettingsCodec.decode(text)
    }

    fun update(settings: AppSettings) {
        _state.value = settings
        prefs.edit().putString(KEY_SETTINGS_JSON, SettingsCodec.encode(settings)).apply()
    }

    fun setTtsEngine(p: TtsEnginePref) = update(_state.value.copy(ttsEngine = p))
    fun setTheme(m: ThemeMode) = update(_state.value.copy(themeMode = m))
    fun setUseVad(b: Boolean) = update(_state.value.copy(useVad = b))
    fun setOverlay(b: Boolean) = update(_state.value.copy(overlayOnImage = b))

    companion object {
        private const val KEY_SETTINGS_JSON = "settings_json_v1"
    }
}
