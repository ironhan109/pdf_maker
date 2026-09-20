package com.ebookmaker.app.domain.model

enum class SessionStatus {
    IDLE,
    SCANNING,
    PAUSED,
    PROCESSING,
    EXPORTING,
    DONE
}

data class ScanSession(
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: SessionStatus = SessionStatus.IDLE,
    val pageCount: Int = 0,
    val pdfPath: String? = null
)
