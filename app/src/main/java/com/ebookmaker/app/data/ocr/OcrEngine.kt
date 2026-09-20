package com.ebookmaker.app.data.ocr

import android.graphics.Bitmap
import android.graphics.RectF
import java.io.File

data class OcrLine(
    val text: String,
    val boundingBox: RectF
)

data class OcrBlock(
    val text: String,
    val boundingBox: RectF,
    val lines: List<OcrLine>
)

data class OcrResult(
    val fullText: String,
    val blocks: List<OcrBlock>
)

interface OcrEngine {
    suspend fun recognizeText(bitmap: Bitmap): OcrResult
    suspend fun recognizeText(imageFile: File): OcrResult
}
