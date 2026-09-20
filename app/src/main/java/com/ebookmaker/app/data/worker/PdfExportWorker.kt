package com.ebookmaker.app.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ebookmaker.app.EbookApplication
import com.ebookmaker.app.domain.model.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfExportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SESSION_ID = "SESSION_ID"
        const val KEY_PROGRESS_CURRENT = "PROGRESS_CURRENT"
        const val KEY_PROGRESS_TOTAL = "PROGRESS_TOTAL"
        const val KEY_RESULT_PDF_PATH = "RESULT_PDF_PATH"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId == -1L) return@withContext Result.failure()

        val repository = EbookApplication.instance.sessionRepository

        try {
            repository.updateSessionStatus(sessionId, SessionStatus.PROCESSING)

            val exportResult = repository.exportPdf(sessionId) { current, total ->
                setProgressAsync(
                    workDataOf(
                        KEY_PROGRESS_CURRENT to current,
                        KEY_PROGRESS_TOTAL to total
                    )
                )
            }

            if (exportResult.isSuccess) {
                val pdfFile = exportResult.getOrThrow()
                Result.success(workDataOf(KEY_RESULT_PDF_PATH to pdfFile.absolutePath))
            } else {
                repository.updateSessionStatus(sessionId, SessionStatus.PAUSED)
                Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            repository.updateSessionStatus(sessionId, SessionStatus.PAUSED)
            Result.failure()
        }
    }
}
