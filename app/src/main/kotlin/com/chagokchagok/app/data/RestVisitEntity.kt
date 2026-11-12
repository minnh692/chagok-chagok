package com.chagokchagok.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A rest area collected on one day. Only the rest-area id (from the bundled public data) and the date are
 * stored: no coordinates, no time of day. Rest areas do not stamp the si/gun/gu they are in.
 */
@Entity(tableName = "rest_visits", indices = [Index(value = ["rest_area_id", "date"], unique = true)])
data class RestVisitEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  /** "<route no>:<name>", see RestAreaCatalog. */
  @ColumnInfo(name = "rest_area_id") val restAreaId: String,
  /** Local calendar date, yyyy-MM-dd. */
  val date: String,
)

@Dao
interface RestVisitDao {
  @Query("SELECT * FROM rest_visits ORDER BY date")
  fun observeAll(): Flow<List<RestVisitEntity>>

  @Query("SELECT * FROM rest_visits WHERE rest_area_id = :restAreaId")
  suspend fun visitsTo(restAreaId: String): List<RestVisitEntity>

  /** Returns -1 when the rest area was already collected that day. */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insert(visit: RestVisitEntity): Long
}
