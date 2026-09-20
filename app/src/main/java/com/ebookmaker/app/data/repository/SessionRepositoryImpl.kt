package com.ebookmaker.app.data.repository

import android.graphics.Bitmap
import com.ebookmaker.app.data.local.FileManager
import com.ebookmaker.app.data.local.dao.ScanPageDao
import com.ebookmaker.app.data.local.dao.ScanSessionDao
import com.ebookmaker.app.data.local.entity.ScanPageEntity
import com.ebookmaker.app.data.local.entity.ScanSessionEntity
import com.ebookmaker.app.data.pdf.SearchablePdfBuilder
import com.ebookmaker.app.data.vision.ImageProcessor
import com.ebookmaker.app.domain.model.EnhancementMode
import com.ebookmaker.app.domain.model.PageStatus
import com.ebookmaker.app.domain.model.Quadrilateral
import com.ebookmaker.app.domain.model.ScanPage
import com.ebookmaker.app.domain.model.ScanSession
import com.ebookmaker.app.domain.model.SessionStatus
import com.ebookmaker.app.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class SessionRepositoryImpl(
    private val sessionDao: ScanSessionDao,
    private val pageDao: ScanPageDao,
    private val fileManager: FileManager,
    private val pdfBuilder: SearchablePdfBuilder
) : SessionRepository {

    override fun getAllSessions(): Flow<List<ScanSession>> {
        return sessionDao.getAllSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getSession(sessionId: Long): Flow<ScanSession?> {
        return sessionDao.getSessionById(sessionId).map { it?.toDomain() }
    }

    override fun getPagesForSession(sessionId: Long): Flow<List<ScanPage>> {
        return pageDao.getPagesForSession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getPageListDirect(sessionId: Long): List<ScanPage> = withContext(Dispatchers.IO) {
        pageDao.getPagesForSessionDirect(sessionId).map { it.toDomain() }
    }

    override suspend fun createSession(title: String): Long = withContext(Dispatchers.IO) {
        val sessionEntity = ScanSessionEntity(
            title = title.ifBlank { "전자책 스캔 ${System.currentTimeMillis()}" },
            status = SessionStatus.SCANNING.name,
            createdAt = System.currentTimeMillis()
        )
        sessionDao.insertSession(sessionEntity)
    }

    override suspend fun updateSessionStatus(sessionId: Long, status: SessionStatus) = withContext(Dispatchers.IO) {
        sessionDao.updateStatus(sessionId, status.name)
    }

    override suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        fileManager.deleteSessionFiles(sessionId)
        sessionDao.deleteSession(sessionId)
    }

    override suspend fun addCapturedPage(
        sessionId: Long,
        tempImageFile: File,
        detectedQuad: Quadrilateral?,
        enhancementMode: EnhancementMode
    ): ScanPage = withContext(Dispatchers.IO) {
        var processedBitmap: Bitmap? = null
        var thumbBitmap: Bitmap? = null

        try {
            val currentPageCount = pageDao.getPageCount(sessionId)
            val newIndex = currentPageCount + 1

            // Process image: warp perspective + apply enhancement
            processedBitmap = ImageProcessor.processCapturedImage(
                imageFile = tempImageFile,
                detectedQuad = detectedQuad,
                enhancementMode = enhancementMode
            )

            // Generate thumbnail
            thumbBitmap = ImageProcessor.createThumbnail(processedBitmap)

            // Save to disk
            val imageFile = fileManager.saveProcessedImage(sessionId, newIndex, processedBitmap)
            val thumbFile = fileManager.saveThumbnail(sessionId, newIndex, thumbBitmap)

            // Insert into Room
            val entity = ScanPageEntity(
                sessionId = sessionId,
                pageIndex = newIndex,
                imagePath = imageFile.absolutePath,
                thumbnailPath = thumbFile.absolutePath,
                status = PageStatus.PROCESSED.name,
                createdAt = System.currentTimeMillis()
            )
            val pageId = pageDao.insertPage(entity)
            sessionDao.updatePageCount(sessionId, newIndex)

            entity.copy(id = pageId).toDomain()
        } finally {
            // Immediate bitmap recycling to prevent OOM
            processedBitmap?.recycle()
            thumbBitmap?.recycle()
            // Clean up temporary capture file
            if (tempImageFile.exists()) {
                tempImageFile.delete()
            }
        }
    }

    override suspend fun deletePage(pageId: Long) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        fileManager.deleteFile(page.imagePath)
        fileManager.deleteFile(page.thumbnailPath)
        pageDao.deletePageById(pageId)

        val remainingCount = pageDao.getPageCount(page.sessionId)
        sessionDao.updatePageCount(page.sessionId, remainingCount)
    }

    override suspend fun reorderPages(sessionId: Long, pageOrder: List<Long>) = withContext(Dispatchers.IO) {
        pageOrder.forEachIndexed { index, pageId ->
            pageDao.updatePageIndex(pageId, index + 1)
        }
    }

    override suspend fun exportPdf(
        sessionId: Long,
        onProgress: (current: Int, total: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val session = sessionDao.getSessionDirect(sessionId)
            ?: return@withContext Result.failure(IllegalStateException("Session not found"))

        val pages = pageDao.getPagesForSessionDirect(sessionId).map { it.toDomain() }
        if (pages.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No pages in session"))
        }

        sessionDao.updateStatus(sessionId, SessionStatus.PROCESSING.name)
        val outputFile = fileManager.getPdfOutputFile(sessionId, session.title)

        val result = pdfBuilder.buildSearchablePdf(pages, outputFile, onProgress)

        if (result.isSuccess) {
            sessionDao.updatePdfPath(sessionId, outputFile.absolutePath, SessionStatus.DONE.name)
        } else {
            sessionDao.updateStatus(sessionId, SessionStatus.PAUSED.name)
        }

        result
    }
}
