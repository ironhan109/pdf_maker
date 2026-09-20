package com.ebookmaker.app.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.ebookmaker.app.data.ocr.OcrEngine
import com.ebookmaker.app.domain.model.ScanPage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.max

class SearchablePdfBuilder(
    private val context: Context,
    private val ocrEngine: OcrEngine
) {

    suspend fun buildSearchablePdf(
        pages: List<ScanPage>,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No pages to export"))
        }

        try {
            // Try building with PDFBox first for true PDF text rendering mode 3 (invisible text)
            buildWithPdfBox(pages, outputFile, onProgress)
            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            // Robust fallback using Android Native PdfDocument
            try {
                buildWithAndroidNativePdf(pages, outputFile, onProgress)
                Result.success(outputFile)
            } catch (fallbackEx: Exception) {
                fallbackEx.printStackTrace()
                Result.failure(fallbackEx)
            }
        }
    }

    private suspend fun buildWithPdfBox(
        pages: List<ScanPage>,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit
    ) {
        val document = PDDocument()

        try {
            for ((index, page) in pages.withIndex()) {
                onProgress(index + 1, pages.size)

                val imageFile = File(page.imagePath)
                if (!imageFile.exists()) continue

                // Decode bounds
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
                val imgW = bounds.outWidth.toFloat()
                val imgH = bounds.outHeight.toFloat()

                // Create PDF page with matching dimensions (in points)
                val pdfPage = PDPage(PDRectangle(imgW, imgH))
                document.addPage(pdfPage)

                // Run OCR on page
                val ocrResult = ocrEngine.recognizeText(imageFile)

                // Decode bitmap safely
                var pageBitmap: Bitmap? = null
                try {
                    pageBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (pageBitmap == null) continue

                    val pdImage = JPEGFactory.createFromImage(document, pageBitmap, 0.85f)

                    PDPageContentStream(document, pdfPage, PDPageContentStream.AppendMode.OVERWRITE, true).use { cs ->
                        // 1. Draw page image as background
                        cs.drawImage(pdImage, 0f, 0f, imgW, imgH)

                        // 2. Add invisible text layer (Mode 3: neither fill nor stroke)
                        cs.setRenderingMode(RenderingMode.NEITHER)
                        val font = PDType1Font.HELVETICA

                        for (block in ocrResult.blocks) {
                            for (line in block.lines) {
                                val box = line.boundingBox
                                if (box.isEmpty || line.text.isBlank()) continue

                                // PDF coordinate system origin is bottom-left, Image coordinate is top-left
                                val pdfX = box.left.coerceIn(0f, imgW)
                                val pdfY = (imgH - box.bottom).coerceIn(0f, imgH)
                                val boxHeight = max(8f, box.height())

                                cs.beginText()
                                cs.setFont(font, boxHeight * 0.8f)
                                cs.newLineAtOffset(pdfX, pdfY)
                                // Sanitize text for PDFBox standard font encoding
                                val safeText = line.text.replace("\n", " ").trim()
                                try {
                                    cs.showText(safeText)
                                } catch (fontEx: Exception) {
                                    // Fallback to ASCII approximation if non-latin in Type1
                                    val asciiText = safeText.filter { it.code in 32..126 }
                                    if (asciiText.isNotEmpty()) {
                                        cs.showText(asciiText)
                                    }
                                }
                                cs.endText()
                            }
                        }
                    }
                } finally {
                    pageBitmap?.recycle()
                }
            }

            // Save PDF stream to file
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                document.save(fos)
            }
        } finally {
            document.close()
        }
    }

    /**
     * Fallback PDF builder using Android's native android.graphics.pdf.PdfDocument
     */
    private suspend fun buildWithAndroidNativePdf(
        pages: List<ScanPage>,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit
    ) {
        val pdfDoc = PdfDocument()

        try {
            val textPaint = Paint().apply {
                color = Color.TRANSPARENT // Invisible text layer
                textSize = 14f
                isAntiAlias = true
            }

            for ((index, page) in pages.withIndex()) {
                onProgress(index + 1, pages.size)

                val imageFile = File(page.imagePath)
                if (!imageFile.exists()) continue

                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: continue
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val pdfPage = pdfDoc.startPage(pageInfo)

                try {
                    val canvas: Canvas = pdfPage.canvas
                    // Draw base image
                    canvas.drawBitmap(bitmap, 0f, 0f, null)

                    // OCR and invisible text overlay
                    val ocrResult = ocrEngine.recognizeText(imageFile)
                    for (block in ocrResult.blocks) {
                        for (line in block.lines) {
                            textPaint.textSize = max(10f, line.boundingBox.height() * 0.8f)
                            canvas.drawText(line.text, line.boundingBox.left, line.boundingBox.bottom, textPaint)
                        }
                    }
                } finally {
                    pdfDoc.finishPage(pdfPage)
                    bitmap.recycle()
                }
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                pdfDoc.writeTo(fos)
            }
        } finally {
            pdfDoc.close()
        }
    }
}
