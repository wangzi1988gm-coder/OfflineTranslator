package com.example.offlinetranslator.ui.screen.translate

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.offlinetranslator.data.Language
import com.example.offlinetranslator.ui.component.DirectionPad

@Composable
fun TranslateScreen(
    vm: TranslateViewModel,
    onOpenImage: () -> Unit,
    onOpenConversation: () -> Unit,
) {
    val source by vm.source.collectAsState()
    val result by vm.result.collectAsState()
    val from by vm.from.collectAsState()
    val to by vm.to.collectAsState()
    val busy by vm.busy.collectAsState()
    val status by vm.status.collectAsState()

    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onOpenImage()
    }

    // 严格离线：不调用可能依赖云端的系统语音识别 Intent。
    // 对话页使用应用内 sherpa-onnx ASR；模型未安装时会显示本地提示。
    fun launchOfflineAsr() {
        onOpenConversation()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("离线翻译", style = MaterialTheme.typography.headlineMedium)

        DirectionPad(from = from, to = to, onSwap = { vm.swap() }, onFromSelect = vm::setFrom, onToSelect = vm::setTo)

        OutlinedTextField(
            value = source,
            onValueChange = vm::setSource,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            placeholder = { Text("输入文字，或使用下方按钮识别") },
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChipButton("粘贴", Icons.Default.ContentPaste, { vm.pasteFromClipboard(context) }, Modifier.weight(1f))
            AssistChipButton("离线听写", Icons.Default.Mic, { launchOfflineAsr() }, Modifier.weight(1f))
            AssistChipButton("相册", Icons.Default.PhotoLibrary, { pickImage.launch("image/*") }, Modifier.weight(1f))
            AssistChipButton("相机", Icons.Default.PhotoCamera, onOpenImage, Modifier.weight(1f))
        }

        Button(
            onClick = { vm.translate() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            shape = RoundedCornerShape(14.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text("翻译中…")
            } else {
                Icon(Icons.Default.Translate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("翻译  ${from.flagEmoji}→${to.flagEmoji}")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("译文", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                Spacer(Modifier.height(4.dp))
                val translatedText = result?.translatedText.orEmpty()
                if (translatedText.isBlank()) {
                    Text("译文将显示在此", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                } else {
                    Text(translatedText, style = MaterialTheme.typography.titleMedium)
                }
                if (result?.usedCascade?.isNotEmpty() == true) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "级联: ${result?.usedCascade?.joinToString(" → ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChipButton("朗读", Icons.Default.PlayArrow, { vm.speak(TranslateViewModel.SpeakTarget.TRANSLATION) })
                    AssistChipButton("复制", Icons.Default.ContentCopy, { vm.copyToClipboard(context, translatedText) })
                    AssistChipButton("清空", null, { vm.clear() })
                }
            }
        }

        if (status != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(status ?: "", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ElevatedButton(onClick = onOpenConversation, modifier = Modifier.weight(1f)) {
                Text("对话模式")
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }
            ElevatedButton(onClick = onOpenImage, modifier = Modifier.weight(1f)) {
                Text("图片翻译")
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun AssistChipButton(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
