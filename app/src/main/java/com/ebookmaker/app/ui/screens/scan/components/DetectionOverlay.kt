package com.ebookmaker.app.ui.screens.scan.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ebookmaker.app.domain.model.Quadrilateral
import com.ebookmaker.app.domain.model.ScanningState
import com.ebookmaker.app.ui.theme.ScannerGreen
import com.ebookmaker.app.ui.theme.ScannerRed
import com.ebookmaker.app.ui.theme.ScannerYellow

@Composable
fun DetectionOverlay(
    modifier: Modifier = Modifier,
    quad: Quadrilateral?,
    frameWidth: Int,
    frameHeight: Int,
    scanningState: ScanningState
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (quad == null || frameWidth <= 0 || frameHeight <= 0) return@Canvas

        val viewW = size.width
        val viewH = size.height

        // CameraX preview is usually rotated or scaled; compute scale preserving aspect ratio
        val scaleX = viewW / frameWidth.toFloat()
        val scaleY = viewH / frameHeight.toFloat()

        fun mapPoint(x: Float, y: Float): Offset {
            return Offset(x * scaleX, y * scaleY)
        }

        val pTL = mapPoint(quad.topLeft.x, quad.topLeft.y)
        val pTR = mapPoint(quad.topRight.x, quad.topRight.y)
        val pBR = mapPoint(quad.bottomRight.x, quad.bottomRight.y)
        val pBL = mapPoint(quad.bottomLeft.x, quad.bottomLeft.y)

        val (lineColor, strokeWidth) = when (scanningState) {
            is ScanningState.Capturing -> Pair(ScannerGreen, 6.dp.toPx())
            is ScanningState.Stabilizing -> Pair(ScannerYellow, 4.dp.toPx())
            is ScanningState.WaitPageTurn -> Pair(Color.Cyan, 3.dp.toPx())
            is ScanningState.Paused -> Pair(Color.Gray, 2.dp.toPx())
            else -> Pair(Color.White.copy(alpha = 0.6f), 3.dp.toPx())
        }

        // Draw quad fill highlight
        val path = Path().apply {
            moveTo(pTL.x, pTL.y)
            lineTo(pTR.x, pTR.y)
            lineTo(pBR.x, pBR.y)
            lineTo(pBL.x, pBL.y)
            close()
        }

        val fillColor = lineColor.copy(alpha = if (scanningState is ScanningState.Capturing) 0.25f else 0.10f)
        drawPath(path, color = fillColor)

        // Draw boundary stroke
        val stroke = Stroke(
            width = strokeWidth,
            pathEffect = if (scanningState is ScanningState.Searching) PathEffect.dashPathEffect(floatArrayOf(20f, 15f)) else null
        )
        drawPath(path, color = lineColor, style = stroke)

        // Draw 4 corner circles
        val cornerRadius = 10.dp.toPx()
        listOf(pTL, pTR, pBR, pBL).forEach { pt ->
            drawCircle(color = Color.White, radius = cornerRadius, center = pt)
            drawCircle(color = lineColor, radius = cornerRadius - 2.dp.toPx(), center = pt)
        }
    }
}
