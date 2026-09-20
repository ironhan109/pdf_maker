package com.ebookmaker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ebookmaker.app.data.local.entity.ScanPageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanPageDao {
    @Query("SELECT * FROM scan_pages WHERE sessionId = :sessionId ORDER BY pageIndex ASC")
    fun getPagesForSession(sessionId: Long): Flow<List<ScanPageEntity>>

    @Query("SELECT * FROM scan_pages WHERE sessionId = :sessionId ORDER BY pageIndex ASC")
    suspend fun getPagesForSessionDirect(sessionId: Long): List<ScanPageEntity>

    @Query("SELECT * FROM scan_pages WHERE id = :pageId LIMIT 1")
    suspend fun getPageById(pageId: Long): ScanPageEntity?

    @Query("SELECT COUNT(*) FROM scan_pages WHERE sessionId = :sessionId")
    suspend fun getPageCount(sessionId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: ScanPageEntity): Long

    @Update
    suspend fun updatePage(page: ScanPageEntity)

    @Query("UPDATE scan_pages SET pageIndex = :newIndex WHERE id = :pageId")
    suspend fun updatePageIndex(pageId: Long, newIndex: Int)

    @Query("UPDATE scan_pages SET ocrText = :ocrText, status = :status WHERE id = :pageId")
    suspend fun updatePageOcr(pageId: Long, ocrText: String, status: String)

    @Query("DELETE FROM scan_pages WHERE id = :pageId")
    suspend fun deletePageById(pageId: Long)

    @Query("DELETE FROM scan_pages WHERE sessionId = :sessionId")
    suspend fun deletePagesBySession(sessionId: Long)
}
