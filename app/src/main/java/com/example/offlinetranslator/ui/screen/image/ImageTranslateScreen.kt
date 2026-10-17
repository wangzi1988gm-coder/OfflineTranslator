package com.example.offlinetranslator.ui.screen.image

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.offlinetranslator.data.Language
import com.example.offlinetranslator.ui.component.DirectionPad

@Composable
fun ImageTranslateScreen(vm: ImageTranslateViewModel) {
    val bitmap by vm.bitmap.collectAsState()
    val from by vm.fromLang.collectAsState()
    val to by vm.toLang.collectAsState()
    val mode by vm.mode.collectAsState()
    val ocrResult by vm.ocrResult.collectAsState()
    val translations by vm.translations.collectAsState()
    val busy by vm.busy.collectAsState()
    val status by vm.status.collectAsState()
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { vm.loadFromUri(context, it) }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // CameraX launch handled in CameraActivity
        }
    }

    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        bmp?.let { vm.loadFromBitmap(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("图片翻译", style = MaterialTheme.typography.headlineMedium)
        Text(
            "支持相机、相册、路牌、菜单、公文模式；OCR + 行级翻译。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { pickImage.launch("image/*") },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("相册")
            }
            OutlinedButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        takePicture.launch(null)
                    } else {
                        cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("相机")
            }
        }

        // 模式选择
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = mode == ImageTranslateViewModel.Mode.GENERAL,
                onClick = { vm.setMode(ImageTranslateViewModel.Mode.GENERAL) },
                label = { Text("通用") },
            )
            FilterChip(
                selected = mode == ImageTranslateViewModel.Mode.SIGN,
                onClick = { vm.setMode(ImageTranslateViewModel.Mode.SIGN) },
                label = { Text("路牌") },
            )
            FilterChip(
                selected = mode == ImageTranslateViewModel.Mode.MENU,
                onClick = { vm.setMode(ImageTranslateViewModel.Mode.MENU) },
                label = { Text("菜单") },
            )
            FilterChip(
                selected = mode == ImageTranslateViewModel.Mode.DOCUMENT,
                onClick = { vm.setMode(ImageTranslateViewModel.Mode.DOCUMENT) },
                label = { Text("公文") },
            )
        }

        DirectionPad(
            from = from,
            to = to,
            onSwap = {
                val a = from
                vm.setFrom(to)
                vm.setTo(a)
            },
            onFromSelect = vm::setFrom,
            onToSelect = vm::setTo,
        )

        // 图片预览
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("请选择图片或拍照", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 翻译按钮
        Button(
            onClick = { vm.translateAll() },
            enabled = !busy && ocrResult != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(Icons.Default.Translate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (busy) "处理中…" else "翻译全部 (${ocrResult?.lines?.size ?: 0} 行)")
        }

        if (status != null) {
            Text(status ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 行级翻译结果
        if (translations.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("行级翻译结果", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { vm.speakAll() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("全部朗读")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items(translations.size) { idx ->
                            val tr = translations[idx]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        tr.line.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        tr.translated,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    if (tr.fallback) {
                                        Text(
                                            "回退翻译",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
