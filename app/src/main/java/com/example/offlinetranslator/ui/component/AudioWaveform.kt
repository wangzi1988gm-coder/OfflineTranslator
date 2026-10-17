package com.example.offlinetranslator.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * 简易波形显示器（用于录音时可视化）。
 */
@Composable
fun AudioWaveform(
    samples: FloatArray,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF0D47A1),
    backgroundColor: Color = Color.Transparent,
    barCount: Int = 48,
) {
    Box(modifier = modifier.background(backgroundColor)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (samples.isEmpty()) return@Canvas
            val step = (samples.size / barCount).coerceAtLeast(1)
            val barWidth = size.width / barCount
            val centerY = size.height / 2f
            for (i in 0 until barCount) {
                val start = i * step
                val end = (start + step).coerceAtMost(samples.size)
                var maxAmp = 0f
                for (j in start until end) {
                    val v = kotlin.math.abs(samples[j])
                    if (v > maxAmp) maxAmp = v
                }
                val h = maxAmp * size.height / 2f
                val x = i * barWidth + barWidth / 2f
                drawLine(
                    color = barColor,
                    start = Offset(x, centerY - h),
                    end = Offset(x, centerY + h),
                    strokeWidth = (barWidth * 0.6f).coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
fun AudioWaveformPlaceholder(modifier: Modifier = Modifier, color: Color = Color(0xFF777777)) {
    Box(modifier = modifier.fillMaxWidth().height(64.dp)) {
        AudioWaveform(samples = FloatArray(0), barColor = color, modifier = Modifier.fillMaxSize())
    }
}
