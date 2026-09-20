package com.ebookmaker.app.data.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.ebookmaker.app.domain.model.EnhancementMode
import com.ebookmaker.app.domain.model.Quadrilateral
import com.ebookmaker.app.domain.model.VisionPoint
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageProcessor {

    /**
     * Loads and processes captured image file with memory-conscious downsampling and perspective warp.
     */
    fun processCapturedImage(
        imageFile: File,
        detectedQuad: Quadrilateral?,
        enhancementMode: EnhancementMode,
        targetMaxDimension: Int = 2400 // ~300 DPI on standard document
    ): Bitmap {
        // Step 1: Decode bounds to calculate inSampleSize
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imageFile.absolutePath, boundsOptions)
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight

        var inSampleSize = 1
        var longest = max(origW, origH)
        while (longest / inSampleSize > targetMaxDimension) {
            inSampleSize *= 2
        }

        // Step 2: Decode scaled bitmap
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decodedBitmap = BitmapFactory.decodeFile(imageFile.absolutePath, decodeOptions)
            ?: throw IllegalStateException("Failed to decode image from ${imageFile.absolutePath}")

        try {
            // Step 3: Perspective Warp if quad is provided
            val warpedBitmap = if (detectedQuad != null) {
                // Scale quad to match decoded bitmap scale
                val scaleX = decodedBitmap.width.toFloat() / origW.toFloat()
                val scaleY = decodedBitmap.height.toFloat() / origH.toFloat()
                val scaledQuad = Quadrilateral(
                    topLeft = VisionPoint(detectedQuad.topLeft.x * scaleX, detectedQuad.topLeft.y * scaleY),
                    topRight = VisionPoint(detectedQuad.topRight.x * scaleX, detectedQuad.topRight.y * scaleY),
                    bottomRight = VisionPoint(detectedQuad.bottomRight.x * scaleX, detectedQuad.bottomRight.y * scaleY),
                    bottomLeft = VisionPoint(detectedQuad.bottomLeft.x * scaleX, detectedQuad.bottomLeft.y * scaleY)
                )
                warpPerspective(decodedBitmap, scaledQuad)
            } else {
                decodedBitmap
            }

            // Step 4: Apply Enhancement Filter
            val enhancedBitmap = applyEnhancement(warpedBitmap, enhancementMode)

            if (warpedBitmap != decodedBitmap && warpedBitmap != enhancedBitmap) {
                warpedBitmap.recycle()
            }
            if (decodedBitmap != enhancedBitmap && decodedBitmap != warpedBitmap) {
                decodedBitmap.recycle()
            }

            return enhancedBitmap
        } catch (e: Exception) {
            if (!decodedBitmap.isRecycled) {
                decodedBitmap.recycle()
            }
            throw e
        }
    }

    /**
     * Warps 4 corners of the quad into a flat rectangular document.
     */
    fun warpPerspective(src: Bitmap, quad: Quadrilateral): Bitmap {
        // Calculate destination dimensions
        val topW = quad.topLeft.distanceTo(quad.topRight)
        val botW = quad.bottomLeft.distanceTo(quad.bottomRight)
        val leftH = quad.topLeft.distanceTo(quad.bottomLeft)
        val rightH = quad.topRight.distanceTo(quad.bottomRight)

        val targetWidth = max(topW, botW).roundToInt().coerceIn(200, 4096)
        val targetHeight = max(leftH, rightH).roundToInt().coerceIn(200, 4096)

        val srcPoints = floatArrayOf(
            quad.topLeft.x, quad.topLeft.y,
            quad.topRight.x, quad.topRight.y,
            quad.bottomRight.x, quad.bottomRight.y,
            quad.bottomLeft.x, quad.bottomLeft.y
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        if (!success) {
            return src
        }

        // Invert to draw into destination bitmap
        val inverse = Matrix()
        matrix.invert(inverse)

        val resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, matrix, paint)

        return resultBitmap
    }

    /**
     * Generates a lightweight thumbnail (max 300px) from a bitmap or file.
     */
    fun createThumbnail(src: Bitmap, maxDim: Int = 300): Bitmap {
        val w = src.width
        val h = src.height
        val scale = min(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val targetW = (w * scale).roundToInt().coerceAtLeast(1)
        val targetH = (h * scale).roundToInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(src, targetW, targetH, true)
    }

    /**
     * Applies color, grayscale, or binarization enhancements.
     */
    fun applyEnhancement(src: Bitmap, mode: EnhancementMode): Bitmap {
        return when (mode) {
            EnhancementMode.COLOR -> {
                // Boost contrast slightly for document legibility
                adjustContrastAndBrightness(src, contrast = 1.15f, brightness = 5f)
            }
            EnhancementMode.GRAYSCALE -> {
                toGrayscale(src)
            }
            EnhancementMode.BINARIZED -> {
                adaptiveBinarize(src)
            }
        }
    }

    private fun adjustContrastAndBrightness(src: Bitmap, contrast: Float, brightness: Float): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    /**
     * Fast integral-image-based adaptive binarization for crisp text scanning.
     */
    fun adaptiveBinarize(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        // Convert to grayscale luminance array
        val gray = IntArray(width * height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (r * 77 + g * 150 + b * 29) shr 8
        }

        // Integral image for fast local mean computation
        val integral = LongArray(width * height)
        for (y in 0 until height) {
            var sum = 0L
            val rowOffset = y * width
            val prevRowOffset = (y - 1) * width
            for (x in 0 until width) {
                sum += gray[rowOffset + x]
                integral[rowOffset + x] = sum + if (y > 0) integral[prevRowOffset + x] else 0L
            }
        }

        val windowSize = max(width, height) / 32
        val halfW = windowSize / 2
        val outPixels = IntArray(width * height)

        for (y in 0 until height) {
            val y1 = max(0, y - halfW)
            val y2 = min(height - 1, y + halfW)
            val rowOffset = y * width

            for (x in 0 until width) {
                val x1 = max(0, x - halfW)
                val x2 = min(width - 1, x + halfW)
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)

                val sum = integral[y2 * width + x2] -
                        (if (x1 > 0) integral[y2 * width + (x1 - 1)] else 0L) -
                        (if (y1 > 0) integral[(y1 - 1) * width + x2] else 0L) +
                        (if (x1 > 0 && y1 > 0) integral[(y1 - 1) * width + (x1 - 1)] else 0L)

                val localMean = (sum / count).toInt()
                val pixelVal = gray[rowOffset + x]

                // Threshold with 10% bias for text contrast
                outPixels[rowOffset + x] = if (pixelVal < localMean * 0.90f) {
                    Color.BLACK
                } else {
                    Color.WHITE
                }
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Sorts four points into [TopLeft, TopRight, BottomRight, BottomLeft].
     */
    fun orderPoints(pts: List<VisionPoint>): Quadrilateral {
        require(pts.size == 4) { "Quadrilateral requires exactly 4 points" }

        // TopLeft has min (x + y), BottomRight has max (x + y)
        // TopRight has min (y - x), BottomLeft has max (y - x)
        val sortedBySum = pts.sortedBy { it.x + it.y }
        val topLeft = sortedBySum.first()
        val bottomRight = sortedBySum.last()

        val remaining = pts.filter { it != topLeft && it != bottomRight }
        val (topRight, bottomLeft) = if (remaining[0].x > remaining[1].x) {
            Pair(remaining[0], remaining[1])
        } else {
            Pair(remaining[1], remaining[0])
        }

        return Quadrilateral(topLeft, topRight, bottomRight, bottomLeft)
    }
}
