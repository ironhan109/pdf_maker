package com.ebookmaker.app.data.vision

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.ebookmaker.app.domain.model.DetectionResult
import com.ebookmaker.app.domain.model.Quadrilateral
import com.ebookmaker.app.domain.model.VisionPoint
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FrameAnalyzer(
    private val onDetectionResult: (result: DetectionResult, width: Int, height: Int) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalysisTimestamp = 0L
    private val targetIntervalMs = 100L // ~10 fps

    private var previousFrameBuffer: ByteArray? = null
    private var prevBufferWidth = 0
    private var prevBufferHeight = 0

    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalysisTimestamp < targetIntervalMs) {
            image.close()
            return
        }
        lastAnalysisTimestamp = currentTimestamp

        try {
            val planes = image.planes
            if (planes.isEmpty()) {
                image.close()
                return
            }

            val yBuffer = planes[0].buffer
            val width = image.width
            val height = image.height
            val rowStride = planes[0].rowStride
            val pixelStride = planes[0].pixelStride

            // Extract downsampled luminance for fast detection (stride 2 or 4)
            val step = 4
            val sampleW = width / step
            val sampleH = height / step
            val samplePixels = ByteArray(sampleW * sampleH)

            extractLuminance(yBuffer, width, height, rowStride, pixelStride, step, samplePixels, sampleW, sampleH)

            // 1. Calculate Sharpness (Laplacian variance on sample)
            val sharpness = calculateLaplacianVariance(samplePixels, sampleW, sampleH)

            // 2. Calculate Frame Difference (Page Turn Detection)
            val frameDiff = calculateFrameDiff(samplePixels, sampleW, sampleH)

            // 3. Detect Page Quadrilateral
            val quadInSample = detectPageQuad(samplePixels, sampleW, sampleH)

            // Scale detected quad back to full frame coordinates
            val fullQuad = quadInSample?.let { q ->
                Quadrilateral(
                    topLeft = VisionPoint(q.topLeft.x * step, q.topLeft.y * step),
                    topRight = VisionPoint(q.topRight.x * step, q.topRight.y * step),
                    bottomRight = VisionPoint(q.bottomRight.x * step, q.bottomRight.y * step),
                    bottomLeft = VisionPoint(q.bottomLeft.x * step, q.bottomLeft.y * step)
                )
            }

            val result = DetectionResult(
                quad = fullQuad,
                sharpness = sharpness,
                isStable = false, // Managed by state machine
                frameDiff = frameDiff
            )

            onDetectionResult(result, width, height)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }

    private fun extractLuminance(
        yBuffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        step: Int,
        outSample: ByteArray,
        sampleW: Int,
        sampleH: Int
    ) {
        yBuffer.rewind()
        var outIdx = 0
        for (y in 0 until sampleH) {
            val srcY = y * step
            val rowStart = srcY * rowStride
            for (x in 0 until sampleW) {
                val srcX = x * step
                val bytePos = rowStart + srcX * pixelStride
                if (bytePos < yBuffer.limit()) {
                    outSample[outIdx] = yBuffer.get(bytePos)
                }
                outIdx++
            }
        }
    }

    private fun calculateLaplacianVariance(pixels: ByteArray, width: Int, height: Int): Float {
        if (width < 3 || height < 3) return 0f

        // Sample central 50% region for focus/sharpness
        val startX = width / 4
        val endX = width * 3 / 4
        val startY = height / 4
        val endY = height * 3 / 4

        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        for (y in startY until endY) {
            val rowOffset = y * width
            for (x in startX until endX) {
                val center = pixels[rowOffset + x].toInt() and 0xFF
                val top = pixels[(y - 1) * width + x].toInt() and 0xFF
                val bottom = pixels[(y + 1) * width + x].toInt() and 0xFF
                val left = pixels[rowOffset + (x - 1)].toInt() and 0xFF
                val right = pixels[rowOffset + (x + 1)].toInt() and 0xFF

                // Laplacian kernel [ [0, 1, 0], [1, -4, 1], [0, 1, 0] ]
                val lap = (top + bottom + left + right) - (4 * center)
                sum += lap
                sumSq += lap * lap
                count++
            }
        }

        if (count == 0) return 0f
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return variance.toFloat().coerceAtLeast(0f)
    }

    private fun calculateFrameDiff(current: ByteArray, width: Int, height: Int): Float {
        val prev = previousFrameBuffer
        if (prev == null || prev.size != current.size) {
            previousFrameBuffer = current.clone()
            prevBufferWidth = width
            prevBufferHeight = height
            return 0f
        }

        var totalDiff = 0L
        for (i in current.indices) {
            val c = current[i].toInt() and 0xFF
            val p = prev[i].toInt() and 0xFF
            totalDiff += abs(c - p)
        }

        // Copy current to previous
        System.arraycopy(current, 0, prev, 0, current.size)

        return (totalDiff.toFloat() / current.size.toFloat())
    }

    /**
     * Page quadrilateral detection in the sampled luminance buffer.
     * Uses threshold gradient scanning and convex boundary points to find page quad.
     */
    private fun detectPageQuad(pixels: ByteArray, width: Int, height: Int): Quadrilateral? {
        // Calculate average background luminance
        var totalLum = 0L
        for (p in pixels) totalLum += (p.toInt() and 0xFF)
        val avgLum = (totalLum / pixels.size).toInt()

        // Page paper is typically brighter than surrounding desk/background
        val threshold = max(100, min(220, avgLum + 20))

        // Find bounding envelope: scan rays from center or scan borders
        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0

        val step = 2
        var detectedCount = 0

        for (y in 0 until height step step) {
            val rowOffset = y * width
            for (x in 0 until width step step) {
                val lum = pixels[rowOffset + x].toInt() and 0xFF
                if (lum > threshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    detectedCount++
                }
            }
        }

        // Check if detected area is meaningful (>15% of frame)
        val totalSamplePoints = (width / step) * (height / step)
        if (detectedCount < totalSamplePoints * 0.15f || maxX <= minX || maxY <= minY) {
            // Default fallback: 80% inner rect if page fills frame
            val marginX = width * 0.10f
            val marginY = height * 0.10f
            return Quadrilateral(
                topLeft = VisionPoint(marginX, marginY),
                topRight = VisionPoint(width - marginX, marginY),
                bottomRight = VisionPoint(width - marginX, height - marginY),
                bottomLeft = VisionPoint(marginX, height - marginY)
            )
        }

        // Construct 4 corners from envelope with edge refinement
        val left = minX.toFloat().coerceAtLeast(width * 0.05f)
        val right = maxX.toFloat().coerceAtMost(width * 0.95f)
        val top = minY.toFloat().coerceAtLeast(height * 0.05f)
        val bottom = maxY.toFloat().coerceAtMost(height * 0.95f)

        return Quadrilateral(
            topLeft = VisionPoint(left, top),
            topRight = VisionPoint(right, top),
            bottomRight = VisionPoint(right, bottom),
            bottomLeft = VisionPoint(left, bottom)
        )
    }
}
