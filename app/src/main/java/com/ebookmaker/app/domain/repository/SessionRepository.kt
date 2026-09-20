package com.ebookmaker.app.domain.repository

import com.ebookmaker.app.domain.model.EnhancementMode
import com.ebookmaker.app.domain.model.Quadrilateral
import com.ebookmaker.app.domain.model.ScanPage
import com.ebookmaker.app.domain.model.ScanSession
import com.ebookmaker.app.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow
import java.io.File

interface SessionRepository {
    fun getAllSessions(): Flow<List<ScanSession>>
    fun getSession(sessionId: Long): Flow<ScanSession?>
    fun getPagesForSession(sessionId: Long): Flow<List<ScanPage>>
    suspend fun getPageListDirect(sessionId: Long): List<ScanPage>

    suspend fun createSession(title: String): Long
    suspend fun updateSessionStatus(sessionId: Long, status: SessionStatus)
    suspend fun deleteSession(sessionId: Long)

    suspend fun addCapturedPage(
        sessionId: Long,
        tempImageFile: File,
        detectedQuad: Quadrilateral?,
        enhancementMode: EnhancementMode
    ): ScanPage

    suspend fun deletePage(pageId: Long)
    suspend fun reorderPages(sessionId: Long, pageOrder: List<Long>)

    suspend fun exportPdf(
        sessionId: Long,
        onProgress: (current: Int, total: Int) -> Unit
    ): Result<File>
}
