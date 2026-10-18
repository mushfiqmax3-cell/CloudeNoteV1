package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalAppThemeMode

@Composable
fun CloudNoteLogo(
    size: Dp = 36.dp,
    isOnline: Boolean = true,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppThemeMode.current
    val cornerRadius = size * 0.26f

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(theme.accent)
            .border(1.dp, theme.border, RoundedCornerShape(cornerRadius))
            .semantics { contentDescription = "CloudNote Studio Logo" }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val fgColor = theme.accentFg
            val strokeWidth = w * 0.075f

            // Notebook outline inside squircle
            val padX = w * 0.24f
            val padY = h * 0.21f
            val rectW = w - (padX * 2)
            val rectH = h - (padY * 2)

            drawRoundRect(
                color = fgColor,
                topLeft = Offset(padX, padY),
                size = Size(rectW, rectH),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                style = Stroke(width = strokeWidth)
            )

            // Three horizontal text lines inside notebook
            val lineStartX = padX + rectW * 0.22f
            val line1EndX = padX + rectW * 0.65f
            val line2EndX = padX + rectW * 0.78f
            val line3EndX = padX + rectW * 0.54f

            val y1 = padY + rectH * 0.34f
            val y2 = padY + rectH * 0.54f
            val y3 = padY + rectH * 0.74f

            drawLine(
                color = fgColor,
                start = Offset(lineStartX, y1),
                end = Offset(line1EndX, y1),
                strokeWidth = strokeWidth * 0.85f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = fgColor,
                start = Offset(lineStartX, y2),
                end = Offset(line2EndX, y2),
                strokeWidth = strokeWidth * 0.85f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = fgColor,
                start = Offset(lineStartX, y3),
                end = Offset(line3EndX, y3),
                strokeWidth = strokeWidth * 0.85f,
                cap = StrokeCap.Round
            )

            // Sync status dot in top-right of notebook
            val dotColor = if (isOnline) Color(0xFF10B981) else Color(0xFFF59E0B)
            drawCircle(
                color = dotColor,
                radius = w * 0.085f,
                center = Offset(padX + rectW * 0.84f, padY + rectH * 0.22f)
            )
        }
    }
}
