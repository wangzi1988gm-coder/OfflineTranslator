package com.example.offlinetranslator.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.offlinetranslator.data.ThemeMode
import com.example.offlinetranslator.data.TtsEnginePref

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)

        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("朗读引擎", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.ttsEngine == TtsEnginePref.SYSTEM_PREFERRED,
                        onClick = { vm.setTtsEngine(TtsEnginePref.SYSTEM_PREFERRED) },
                        label = { Text("系统") },
                    )
                    FilterChip(
                        selected = settings.ttsEngine == TtsEnginePref.OFFLINE_VITS_ONLY,
                        onClick = { vm.setTtsEngine(TtsEnginePref.OFFLINE_VITS_ONLY) },
                        label = { Text("仅离线 VITS") },
                    )
                    FilterChip(
                        selected = settings.ttsEngine == TtsEnginePref.OFFLINE_VITS_FALLBACK_SYSTEM,
                        onClick = { vm.setTtsEngine(TtsEnginePref.OFFLINE_VITS_FALLBACK_SYSTEM) },
                        label = { Text("VITS 兜底") },
                    )
                }
            }
        }

        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("主题", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.themeMode == ThemeMode.FOLLOW_SYSTEM,
                        onClick = { vm.setTheme(ThemeMode.FOLLOW_SYSTEM) },
                        label = { Text("跟随系统") },
                    )
                    FilterChip(
                        selected = settings.themeMode == ThemeMode.LIGHT,
                        onClick = { vm.setTheme(ThemeMode.LIGHT) },
                        label = { Text("浅色") },
                    )
                    FilterChip(
                        selected = settings.themeMode == ThemeMode.DARK,
                        onClick = { vm.setTheme(ThemeMode.DARK) },
                        label = { Text("深色") },
                    )
                }
            }
        }

        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("端点检测 (VAD)", style = MaterialTheme.typography.titleMedium)
                        Text("录音时智能检测停顿；适合对话模式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.useVad, onCheckedChange = { vm.setUseVad(it) })
                }
            }
        }

        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("覆盖式双语图", style = MaterialTheme.typography.titleMedium)
                        Text("图片翻译时把译文直接覆盖到原图上", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.overlayOnImage, onCheckedChange = { vm.setOverlay(it) })
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(
                    "应用不申请 INTERNET 权限。翻译、ASR、OCR、TTS 均在本地执行。" +
                            "塔吉克语通过俄语模型做级联（zh↔ru + ru↔tg）。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
