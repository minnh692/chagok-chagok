package com.chagokchagok.app.ui.map

import com.chagokchagok.app.data.CompanionType
import com.chagokchagok.app.data.StampBook

/** Map tabs: all stamps, or only the regions stamped with one companion. */
enum class MapFilter(val label: String, val companion: CompanionType?) {
  MINE("나의 지도", null),
  FAMILY("우리 가족", CompanionType.FAMILY),
  PARTNER("연인", CompanionType.PARTNER),
  FRIEND("친구", CompanionType.FRIEND),
}

/** How a region is filled on the map. Regions not listed use the land color. */
enum class Fill { BEFORE, TODAY }

/**
 * Which regions the map fills for [filter], and whether each counts as "today".
 * On a companion tab a region is "today" only if today's stamp there was with that companion.
 */
fun mapFills(book: StampBook, filter: MapFilter): Map<String, Fill> {
  val companion = filter.companion
  val codes = companion?.let(book::codesWith) ?: book.stampedCodes
  val todayCodes = if (companion == null) {
    book.todayCodes
  } else {
    book.todayStamps.filter { it.companion == companion.key }.mapTo(HashSet()) { it.regionCode }
  }
  return codes.associateWith { if (it in todayCodes) Fill.TODAY else Fill.BEFORE }
}
