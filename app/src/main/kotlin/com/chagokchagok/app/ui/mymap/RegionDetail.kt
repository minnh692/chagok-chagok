package com.chagokchagok.app.ui.mymap

import com.chagokchagok.app.data.CompanionType
import com.chagokchagok.app.data.StampBook
import com.chagokchagok.app.region.Region

/**
 * What the "선택한 지역" panel shows for one region. [firstDate] is null when the region has no stamp yet.
 * [memos] are the non-empty memos of every visit, newest first.
 */
data class RegionDetail(
  val code: String,
  val name: String,
  val sido: String,
  val firstDate: String?,
  val visitCount: Int,
  val companions: List<CompanionType>,
  val memos: List<String>,
) {
  val stamped: Boolean get() = firstDate != null

  companion object {
    fun of(region: Region, book: StampBook): RegionDetail {
      val visits = book.visits(region.code)
      return RegionDetail(
        code = region.code,
        name = region.name,
        sido = region.sido,
        firstDate = visits.firstOrNull()?.date,
        visitCount = visits.size,
        // In CompanionType order so the line reads the same every time.
        companions = visits.mapNotNull { CompanionType.fromKey(it.companion) }.distinct().sortedBy { it.ordinal },
        memos = visits.asReversed().mapNotNull { it.memo?.takeIf(String::isNotBlank) },
      )
    }
  }
}
