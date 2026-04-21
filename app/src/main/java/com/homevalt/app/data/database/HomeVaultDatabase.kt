package com.homevalt.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(entities = [UploadRequestEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class HomeVaultDatabase : RoomDatabase() {
    abstract fun uploadRequestDao(): UploadRequestDao

    companion object {
        @Volatile
        private var INSTANCE: HomeVaultDatabase? = null

        fun getDatabase(context: Context): HomeVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HomeVaultDatabase::class.java,
                    "homevault_db"
                ).build()
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
