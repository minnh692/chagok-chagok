package com.chagokchagok.app.data

/**
 * Read-only view over all stored stamps for one "today". Everything the UI shows (totals, map fill,
 * today's list, companion filter) is derived here so it always agrees with what is stored.
 */
class StampBook(stamps: List<StampEntity>, val today: String) {
  private val byRegion: Map<String, List<StampEntity>> = stamps.groupBy { it.regionCode }
  private val firstDateByRegion: Map<String, String> = byRegion.mapValues { (_, visits) -> visits.minOf { it.date } }

  /** Every region stamped at least once. */
  val stampedCodes: Set<String> = byRegion.keys

  /** Number of distinct regions stamped: the "n STAMPS" total. */
  val total: Int get() = stampedCodes.size

  /** Today's stamps in the order they were made. */
  val todayStamps: List<StampEntity> = stamps.filter { it.date == today }.sortedBy { it.dayOrder }

  /** Today's stamps for regions never stamped before today. */
  val todayNewStamps: List<StampEntity> = todayStamps.filter { firstDateByRegion[it.regionCode] == today }

  val todayCodes: Set<String> = todayStamps.mapTo(HashSet()) { it.regionCode }

  /** The companion chosen for today, or null when none is set. */
  val todayCompanion: CompanionType? = todayStamps.firstNotNullOfOrNull { CompanionType.fromKey(it.companion) }

  /** Number of days the region was visited. */
  fun visitCount(regionCode: String): Int = byRegion[regionCode]?.size ?: 0

  fun isNewToday(regionCode: String): Boolean = firstDateByRegion[regionCode] == today

  /** Regions that have at least one stamp with [companion]. */
  fun codesWith(companion: CompanionType): Set<String> =
    byRegion.filterValues { visits -> visits.any { it.companion == companion.key } }.keys

  companion object {
    fun empty(today: String) = StampBook(emptyList(), today)
  }
}
