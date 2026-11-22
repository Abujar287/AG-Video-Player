package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlaybackHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VeloDatabase : RoomDatabase() {
    abstract fun playbackHistoryDao(): PlaybackHistoryDao

    companion object {
        @Volatile
        private var instance: VeloDatabase? = null

        fun getInstance(context: Context): VeloDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VeloDatabase::class.java,
                    "ag_video_player.db"
                ).build().also { instance = it }
            }
        }
    }
}
