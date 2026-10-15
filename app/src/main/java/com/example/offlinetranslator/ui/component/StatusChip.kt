package com.example.offlinetranslator.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.offlinetranslator.data.ModelCategory
import com.example.offlinetranslator.data.ModelSource

@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

fun categoryColor(category: ModelCategory): Color = when (category) {
    ModelCategory.TRANSLATOR -> Color(0xFF0D47A1)
    ModelCategory.ASR -> Color(0xFF2E7D32)
    ModelCategory.TTS -> Color(0xFFEF6C00)
    ModelCategory.OCR -> Color(0xFF6A1B9A)
}

fun sourceLabel(source: ModelSource): String = when (source) {
    ModelSource.ASSETS_BUILTIN -> "内置"
    ModelSource.USER_IMPORTED -> "导入"
    ModelSource.SYSTEM -> "系统"
}
