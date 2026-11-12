package com.chagokchagok.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Local-only storage. There is no server and nothing leaves the device. */
@Database(entities = [StampEntity::class, RestVisitEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
  abstract fun stampDao(): StampDao
  abstract fun restVisitDao(): RestVisitDao

  companion object {
    private const val FILE_NAME = "chagok.db"

    /** v2 adds rest-area visits (id + date only). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `rest_visits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`rest_area_id` TEXT NOT NULL, `date` TEXT NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rest_visits_rest_area_id_date` ON `rest_visits` (`rest_area_id`, `date`)")
      }
    }

    @Volatile private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
      instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
          .addMigrations(MIGRATION_1_2)
          .build()
          .also { instance = it }
      }
  }
}
