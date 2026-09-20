package com.ebookmaker.app.domain.model

enum class PageStatus {
    CAPTURED,
    PROCESSED,
    OCR_DONE,
    FAILED
}

data class ScanPage(
    val id: Long = 0,
    val sessionId: Long,
    val index: Int,
    val imagePath: String,
    val thumbnailPath: String,
    val ocrText: String? = null,
    val status: PageStatus = PageStatus.PROCESSED,
    val createdAt: Long = System.currentTimeMillis()
)
