package com.example.offlinetranslator.ui.screen.models

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinetranslator.data.ModelCategory
import com.example.offlinetranslator.data.ModelPack
import com.example.offlinetranslator.data.ModelSlots
import com.example.offlinetranslator.model.ModelManager
import com.example.offlinetranslator.model.ModelStorage
import com.example.offlinetranslator.engine.translator.TranslatorRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 模型管理 ViewModel。
 */
class ModelManageViewModel(
    val modelStorage: ModelStorage,
    private val modelManager: ModelManager,
    private val translatorRouter: TranslatorRouter,
) : ViewModel() {

    val packs: StateFlow<List<ModelPack>> = modelManager.packs
    val loading: StateFlow<Boolean> = modelManager.loading

    suspend fun refresh() {
        modelManager.refresh()
        withContext(Dispatchers.IO) { translatorRouter.preloadAll() }
    }

    fun delete(pack: ModelPack) {
        viewModelScope.launch(Dispatchers.IO) {
            modelManager.deletePack(pack)
        }
    }

    fun importTree(context: Context, treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@launch
            // DocumentFile 树 → 拷贝到 modelStorage.modelsRoot
            copyTreeToModels(context, tree)
            modelManager.refresh()
            translatorRouter.preloadAll()
        }
    }

    private suspend fun copyTreeToModels(context: Context, root: DocumentFile) = withContext(Dispatchers.IO) {
        walk(root).forEach { doc ->
            if (!doc.isFile) return@forEach
            val name = doc.name ?: return@forEach
            // 匹配路径片段（translator/zh-en/model.onnx 等）
            val parentPath = parentRelativePath(root, doc)
            val target = File(modelStorage.modelsRoot, "$parentPath/$name")
            target.parentFile?.mkdirs()
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun walk(node: DocumentFile): Sequence<DocumentFile> = sequence {
        if (node.isFile) {
            yield(node); return@sequence
        }
        for (child in node.listFiles()) yieldAll(walk(child))
    }

    private fun parentRelativePath(root: DocumentFile, file: DocumentFile): String {
        val segs = mutableListOf<String>()
        var cur: DocumentFile? = file.parentFile
        while (cur != null && cur.uri != root.uri) {
            cur.name?.let { segs.add(0, it) }
            cur = cur.parentFile
        }
        return segs.joinToString("/")
    }
}
