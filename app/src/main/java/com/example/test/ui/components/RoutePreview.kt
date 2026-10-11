package com.example.test.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun RoutePreview(
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .sizeIn(minWidth = 30.dp, minHeight = 56.dp)
            .fillMaxWidth()
            .aspectRatio(0.72f)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val start = Offset(size.width * 0.5f, size.height * 0.18f)
            val end = Offset(size.width * 0.5f, size.height * 0.82f)
            drawLine(
                color = outline.copy(alpha = 0.7f),
                start = start,
                end = end,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = primary.copy(alpha = 0.18f),
                radius = 11.dp.toPx(),
                center = start
            )
            drawCircle(
                color = primary,
                radius = 5.dp.toPx(),
                center = start
            )
            drawCircle(
                color = accent.copy(alpha = 0.22f),
                radius = 12.dp.toPx(),
                center = end
            )
            drawCircle(
                color = accent,
                radius = 6.dp.toPx(),
                center = end
            )
            drawCircle(
                color = outline.copy(alpha = 0.35f),
                radius = 18.dp.toPx(),
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
