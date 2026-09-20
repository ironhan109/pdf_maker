package com.ebookmaker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ebookmaker.app.domain.model.ScanSession
import com.ebookmaker.app.domain.model.SessionStatus

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = SessionStatus.IDLE.name,
    val pageCount: Int = 0,
    val pdfPath: String? = null
) {
    fun toDomain(): ScanSession {
        return ScanSession(
            id = id,
            title = title,
            createdAt = createdAt,
            status = runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.IDLE),
            pageCount = pageCount,
            pdfPath = pdfPath
        )
    }

    companion object {
        fun fromDomain(session: ScanSession): ScanSessionEntity {
            return ScanSessionEntity(
                id = session.id,
                title = session.title,
                createdAt = session.createdAt,
                status = session.status.name,
                pageCount = session.pageCount,
                pdfPath = session.pdfPath
            )
        }
    }
}
