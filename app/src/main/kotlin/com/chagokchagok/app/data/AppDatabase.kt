package com.chagokchagok.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Local-only storage. There is no server and nothing leaves the device. */
@Database(entities = [StampEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
  abstract fun stampDao(): StampDao

  companion object {
    private const val FILE_NAME = "chagok.db"

    @Volatile private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
      instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
          .build()
          .also { instance = it }
      }
  }
}
