package com.chagokchagok.app.collection

import com.chagokchagok.app.data.CompanionType
import com.chagokchagok.app.data.StampBook
import com.chagokchagok.app.region.RegionIndex
import java.time.LocalDate

/** Progress of one collection. Everything is computed from stamps at read time; nothing is stored. */
data class CollectionProgress(
  val id: String,
  val kind: Kind,
  /** Short name for chips and lists, e.g. "경기도", "바다를 낀 시·군·구", "가족과 함께". */
  val title: String,
  val done: Int,
  val target: Int,
  /** Region codes that count toward this collection (the whole set, stamped or not). Empty for season. */
  val members: Set<String> = emptySet(),
) {
  enum class Kind { SIDO, COAST, SEASON, COMPANION, REST }

  val complete: Boolean get() = target > 0 && done >= target
  val fraction: Float get() = if (target == 0) 0f else (done.toFloat() / target).coerceAtMost(1f)
  val label: String get() = "$title $done/$target"
}

enum class Season(val label: String) {
  SPRING("봄"), SUMMER("여름"), AUTUMN("가을"), WINTER("겨울");

  companion object {
    /** Mar-May spring, Jun-Aug summer, Sep-Nov autumn, Dec-Feb winter. */
    fun of(date: LocalDate): Season = when (date.monthValue) {
      in 3..5 -> SPRING
      in 6..8 -> SUMMER
      in 9..11 -> AUTUMN
      else -> WINTER
    }
  }
}

/**
 * The collection rules.
 * - 시·도: one set per sido in the data (16: 광주·전남 are one, 전남광주통합특별시). Complete when every region in it is stamped.
 * - 바다를 낀 시·군·구: [CoastalRegions]. Complete when all are stamped.
 * - 두 계절: each region stamped in two different seasons is one completed item ("season:<code>").
 * - 함께: 가족과 10곳, 연인과 5곳, 친구와 5곳 (distinct regions with a stamp with that companion).
 */
class Collections(private val index: RegionIndex, private val coastal: Set<String>) {
  private val sidoMembers: Map<String, Set<String>> =
    index.regions.groupBy { it.sido }.mapValues { (_, list) -> list.mapTo(HashSet()) { it.code } }

  /** Sidos in data order (by region code), so lists are stable. */
  val sidos: List<String> = index.regions.sortedBy { it.code }.map { it.sido }.distinct()

  fun progress(book: StampBook): List<CollectionProgress> {
    val stamped = book.stampedCodes
    val sido = sidos.map { name ->
      val members = sidoMembers.getValue(name)
      CollectionProgress("sido:$name", CollectionProgress.Kind.SIDO, name, members.count { it in stamped }, members.size, members)
    }
    val coast = CollectionProgress(
      COAST_ID, CollectionProgress.Kind.COAST, "바다를 낀 시·군·구", coastal.count { it in stamped }, coastal.size, coastal,
    )
    val season = CollectionProgress(
      SEASON_ID, CollectionProgress.Kind.SEASON, "두 계절", twoSeasonRegions(book).size, SEASON_SHOWCASE,
    )
    val companions = COMPANION_TARGETS.map { (type, target) ->
      val codes = book.codesWith(type)
      CollectionProgress("companion:${type.key}", CollectionProgress.Kind.COMPANION, companionTitle(type), codes.size.coerceAtMost(target), target)
    }
    return sido + coast + season + companions
  }

  /** Regions stamped in at least two different seasons. */
  fun twoSeasonRegions(book: StampBook): Set<String> =
    book.stampedCodes.filterTo(HashSet()) { code ->
      book.visits(code).mapNotNull { runCatching { Season.of(LocalDate.parse(it.date)) }.getOrNull() }.toSet().size >= 2
    }

  /** Ids of everything completed: complete sets plus one "season:<code>" per two-season region. */
  fun completedIds(book: StampBook): Set<String> =
    progress(book).filter { it.kind != CollectionProgress.Kind.SEASON && it.complete }.mapTo(HashSet()) { it.id } +
      twoSeasonRegions(book).map { "$SEASON_ID:$it" }

  /** Collections a new stamp in [code] would move forward (for recommendations). */
  fun advancedBy(code: String, book: StampBook): List<CollectionProgress> =
    progress(book).filter { (it.kind == CollectionProgress.Kind.SIDO || it.kind == CollectionProgress.Kind.COAST) && code in it.members && !it.complete && code !in book.stampedCodes }

  companion object {
    const val COAST_ID = "coast"
    const val SEASON_ID = "season"

    /** The season collection has no natural end; progress is shown against this many regions. */
    const val SEASON_SHOWCASE = 10

    val COMPANION_TARGETS = linkedMapOf(
      CompanionType.FAMILY to 10,
      CompanionType.PARTNER to 5,
      CompanionType.FRIEND to 5,
    )

    // 가족과 / 연인과 / 친구와: 과 after a final consonant, 와 otherwise.
    fun companionTitle(type: CompanionType): String {
      val last = type.label.last()
      val hasFinal = last in '가'..'힣' && (last - '가') % 28 != 0
      return "${type.label}${if (hasFinal) "과" else "와"} 함께"
    }
  }
}
