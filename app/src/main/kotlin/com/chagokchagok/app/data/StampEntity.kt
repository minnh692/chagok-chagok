package com.chagokchagok.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One visit to a region on one day. This is everything the app stores about a visit:
 * no coordinates, no route and no time of day, by design. Do not add such columns.
 */
@Entity(
  tableName = "stamps",
  indices = [Index(value = ["region_code", "date"], unique = true), Index(value = ["date"])],
)
data class StampEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  /** 5-digit si/gun/gu code from the bundled region data. */
  @ColumnInfo(name = "region_code") val regionCode: String,
  /** Local calendar date, yyyy-MM-dd. */
  val date: String,
  /** 1-based order of this stamp among the stamps of the same date. */
  @ColumnInfo(name = "day_order") val dayOrder: Int,
  /** [CompanionType.key], or null when not chosen. */
  val companion: String? = null,
  val memo: String? = null,
)

/** Who the day was spent with. [key] is what gets stored. */
enum class CompanionType(val key: String, val label: String) {
  FAMILY("family", "가족"),
  PARTNER("partner", "연인"),
  FRIEND("friend", "친구"),
  ALONE("alone", "혼자"),
  ;

  companion object {
    fun fromKey(key: String?): CompanionType? = entries.firstOrNull { it.key == key }
  }
}
