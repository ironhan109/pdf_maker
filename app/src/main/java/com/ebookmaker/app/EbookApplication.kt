package com.ebookmaker.app

import android.app.Application
import com.ebookmaker.app.data.local.AppDatabase
import com.ebookmaker.app.data.local.FileManager
import com.ebookmaker.app.data.ocr.MlKitOcrEngine
import com.ebookmaker.app.data.pdf.SearchablePdfBuilder
import com.ebookmaker.app.data.repository.SessionRepositoryImpl
import com.ebookmaker.app.domain.repository.SessionRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class EbookApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var fileManager: FileManager
        private set

    lateinit var sessionRepository: SessionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize PDFBox for Android
        try {
            PDFBoxResourceLoader.init(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initialize Local Storage & Database
        database = AppDatabase.getInstance(this)
        fileManager = FileManager(this)

        val ocrEngine = MlKitOcrEngine()
        val pdfBuilder = SearchablePdfBuilder(this, ocrEngine)

        sessionRepository = SessionRepositoryImpl(
            sessionDao = database.scanSessionDao(),
            pageDao = database.scanPageDao(),
            fileManager = fileManager,
            pdfBuilder = pdfBuilder
        )
    }

    companion object {
        lateinit var instance: EbookApplication
            private set
    }
}
