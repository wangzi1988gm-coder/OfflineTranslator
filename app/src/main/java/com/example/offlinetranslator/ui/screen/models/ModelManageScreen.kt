package com.example.offlinetranslator.ui.screen.models

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.offlinetranslator.data.ModelCategory
import com.example.offlinetranslator.data.ModelPack
import com.example.offlinetranslator.data.ModelSlots
import com.example.offlinetranslator.ui.component.StatusChip
import com.example.offlinetranslator.ui.component.categoryColor
import com.example.offlinetranslator.ui.component.sourceLabel
import kotlinx.coroutines.launch

@Composable
fun ModelManageScreen(
    vm: ModelManageViewModel,
) {
    val packs by vm.packs.collectAsState()
    val loading by vm.loading.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.refresh() }

    val openDir = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // 持久化权限
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Throwable) {}
            vm.importTree(context, it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("模型管理", style = MaterialTheme.typography.headlineMedium)
        Text(
            "应用完全不依赖网络运行。模型可来自内置占位（assets）或从外部目录导入。\n" +
                    "导入路径：\n  ${vm.modelStorage.userHintPath()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { openDir.launch(null) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("导入模型目录")
            }
            OutlinedButton(
                onClick = { scope.launch { vm.refresh() } },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("重新扫描")
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(
                    "已识别模型 ${packs.size} 个，" +
                            "翻译 ${packs.count { it.category == ModelCategory.TRANSLATOR }}，" +
                            "ASR ${packs.count { it.category == ModelCategory.ASR }}，" +
                            "TTS ${packs.count { it.category == ModelCategory.TTS }}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (loading) {
            Text("扫描中…", style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // 槽位分组
            val grouped = ModelSlots.all().groupBy { it.category }
            grouped.forEach { (category, slots) ->
                items(slots) { slot ->
                    val installed = packs.firstOrNull { it.category == category && it.slotKey == slot.key }
                    SlotRow(slot, installed, onDelete = { installed?.let { vm.delete(it) } })
                }
            }
        }
    }
}

@Composable
private fun SlotRow(slot: com.example.offlinetranslator.data.ModelSlot, installed: ModelPack?, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (installed != null) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(slot.displayName.take(1), style = MaterialTheme.typography.titleLarge, color = categoryColor(slot.category))
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(slot.displayName, style = MaterialTheme.typography.titleMedium)
                if (slot.note.isNotBlank()) {
                    Text(slot.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                if (installed != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(
                            text = "已就绪 · ${installed.displaySize()}",
                            color = MaterialTheme.colorScheme.primary,
                        )
                        StatusChip(
                            text = sourceLabel(installed.source),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                } else {
                    StatusChip(text = "未安装", color = MaterialTheme.colorScheme.error)
                }
            }
            if (installed != null && installed.source != com.example.offlinetranslator.data.ModelSource.ASSETS_BUILTIN) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
