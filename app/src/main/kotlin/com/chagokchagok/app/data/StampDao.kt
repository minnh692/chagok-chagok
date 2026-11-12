package com.chagokchagok.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Storage operations only; the stamping rules live in [StampRepository]. */
@Dao
interface StampDao {
  @Query("SELECT * FROM stamps ORDER BY date, day_order")
  fun observeAll(): Flow<List<StampEntity>>

  @Query("SELECT * FROM stamps ORDER BY date, day_order")
  suspend fun getAll(): List<StampEntity>

  /** Fails if the region already has a stamp on that date (unique index). */
  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(stamp: StampEntity): Long

  @Query("UPDATE stamps SET companion = :companion WHERE date = :date")
  suspend fun setCompanionOn(date: String, companion: String?)

  @Query("UPDATE stamps SET memo = :memo WHERE id = :id")
  suspend fun setMemo(id: Long, memo: String?)
}
