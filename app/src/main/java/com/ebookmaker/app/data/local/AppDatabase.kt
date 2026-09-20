package com.ebookmaker.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ebookmaker.app.data.local.dao.ScanPageDao
import com.ebookmaker.app.data.local.dao.ScanSessionDao
import com.ebookmaker.app.data.local.entity.ScanPageEntity
import com.ebookmaker.app.data.local.entity.ScanSessionEntity

@Database(
    entities = [ScanSessionEntity::class, ScanPageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun scanPageDao(): ScanPageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ebook_maker.db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
