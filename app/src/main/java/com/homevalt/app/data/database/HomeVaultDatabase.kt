package com.homevalt.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UploadRequestEntity::class, CachedFileEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class HomeVaultDatabase : RoomDatabase() {
    abstract fun uploadRequestDao(): UploadRequestDao
    abstract fun cachedFileDao(): CachedFileDao

    companion object {
        @Volatile
        private var INSTANCE: HomeVaultDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS cached_files (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        size INTEGER NOT NULL,
                        lastModified INTEGER NOT NULL,
                        cachedAt INTEGER NOT NULL
                    )"""
                )
            }
        }

        fun getDatabase(context: Context): HomeVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HomeVaultDatabase::class.java,
                    "homevault_db"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromUploadStatus(value: UploadStatus): String = value.name

    @TypeConverter
    fun toUploadStatus(value: String): UploadStatus = UploadStatus.valueOf(value)
}
