package com.ebookmaker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ebookmaker.app.domain.model.PageStatus
import com.ebookmaker.app.domain.model.ScanPage

@Entity(
    tableName = "scan_pages",
    foreignKeys = [
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class ScanPageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val pageIndex: Int,
    val imagePath: String,
    val thumbnailPath: String,
    val ocrText: String? = null,
    val status: String = PageStatus.PROCESSED.name,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): ScanPage {
        return ScanPage(
            id = id,
            sessionId = sessionId,
            index = pageIndex,
            imagePath = imagePath,
            thumbnailPath = thumbnailPath,
            ocrText = ocrText,
            status = runCatching { PageStatus.valueOf(status) }.getOrDefault(PageStatus.PROCESSED),
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(page: ScanPage): ScanPageEntity {
            return ScanPageEntity(
                id = page.id,
                sessionId = page.sessionId,
                pageIndex = page.index,
                imagePath = page.imagePath,
                thumbnailPath = page.thumbnailPath,
                ocrText = page.ocrText,
                status = page.status.name,
                createdAt = page.createdAt
            )
        }
    }
}
