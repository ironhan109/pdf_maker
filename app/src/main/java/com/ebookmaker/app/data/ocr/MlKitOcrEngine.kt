package com.ebookmaker.app.data.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File

class MlKitOcrEngine : OcrEngine {

    // Supports Korean + Latin/English text recognition on-device
    private val recognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    override suspend fun recognizeText(bitmap: Bitmap): OcrResult {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(image).await()
            mapVisionTextToOcrResult(visionText)
        } catch (e: Exception) {
            e.printStackTrace()
            OcrResult(fullText = "", blocks = emptyList())
        }
    }

    override suspend fun recognizeText(imageFile: File): OcrResult {
        return try {
            val image = InputImage.fromFilePath(
                com.ebookmaker.app.EbookApplication.instance,
                android.net.Uri.fromFile(imageFile)
            )
            val visionText = recognizer.process(image).await()
            mapVisionTextToOcrResult(visionText)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback via downsampled bitmap decode if Uri fails
            val bm = BitmapFactory.decodeFile(imageFile.absolutePath)
            if (bm != null) {
                try {
                    recognizeText(bm)
                } finally {
                    bm.recycle()
                }
            } else {
                OcrResult(fullText = "", blocks = emptyList())
            }
        }
    }

    private fun mapVisionTextToOcrResult(visionText: Text): OcrResult {
        val blocks = mutableListOf<OcrBlock>()

        for (b in visionText.textBlocks) {
            val bBox = b.boundingBox?.let { RectF(it) } ?: RectF()
            val lines = mutableListOf<OcrLine>()

            for (l in b.lines) {
                val lBox = l.boundingBox?.let { RectF(it) } ?: RectF()
                lines.add(OcrLine(text = l.text, boundingBox = lBox))
            }

            blocks.add(
                OcrBlock(
                    text = b.text,
                    boundingBox = bBox,
                    lines = lines
                )
            )
        }

        return OcrResult(
            fullText = visionText.text,
            blocks = blocks
        )
    }
}
