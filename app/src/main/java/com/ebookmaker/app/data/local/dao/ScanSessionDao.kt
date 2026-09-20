package com.ebookmaker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ebookmaker.app.data.local.entity.ScanSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanSessionDao {
    @Query("SELECT * FROM scan_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<ScanSessionEntity>>

    @Query("SELECT * FROM scan_sessions WHERE id = :sessionId LIMIT 1")
    fun getSessionById(sessionId: Long): Flow<ScanSessionEntity?>

    @Query("SELECT * FROM scan_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionDirect(sessionId: Long): ScanSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScanSessionEntity): Long

    @Update
    suspend fun updateSession(session: ScanSessionEntity)

    @Query("UPDATE scan_sessions SET status = :status WHERE id = :sessionId")
    suspend fun updateStatus(sessionId: Long, status: String)

    @Query("UPDATE scan_sessions SET pageCount = :pageCount WHERE id = :sessionId")
    suspend fun updatePageCount(sessionId: Long, pageCount: Int)

    @Query("UPDATE scan_sessions SET pdfPath = :pdfPath, status = :status WHERE id = :sessionId")
    suspend fun updatePdfPath(sessionId: Long, pdfPath: String, status: String)

    @Query("DELETE FROM scan_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)
}
