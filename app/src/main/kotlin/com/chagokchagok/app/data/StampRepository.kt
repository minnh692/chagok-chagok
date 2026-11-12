package com.chagokchagok.app.data

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What happened when the car was found parked in a region. */
sealed interface StampResult {
  val stamp: StampEntity

  /** First stamp ever for this region. [newToday] counts today's new regions including this one. */
  data class New(override val stamp: StampEntity, val newToday: Int, val total: Int) : StampResult

  /** The region already has today's stamp; nothing was stored. */
  data class AlreadyStamped(override val stamp: StampEntity) : StampResult

  /** The region was stamped on an earlier day; today's visit was added. [visitCount] includes today. */
  data class WelcomeBack(override val stamp: StampEntity, val visitCount: Int) : StampResult
}

/**
 * The stamping rules:
 * - at most one stamp per region per day;
 * - each stamp gets the next order number within its day (1, 2, ...);
 * - a stamp made on a day that already has a companion takes that companion.
 */
class StampRepository(private val dao: StampDao) {
  // One writer at a time so "read, decide, insert" cannot interleave.
  private val writeLock = Mutex()

  fun observeStamps(): Flow<List<StampEntity>> = dao.observeAll()

  suspend fun stamp(regionCode: String, date: LocalDate): StampResult = writeLock.withLock {
    val day = date.toString() // ISO yyyy-MM-dd
    val all = dao.getAll()
    val visits = all.filter { it.regionCode == regionCode }
    visits.firstOrNull { it.date == day }?.let { return@withLock StampResult.AlreadyStamped(it) }

    val sameDay = all.filter { it.date == day }
    val draft = StampEntity(
      regionCode = regionCode,
      date = day,
      dayOrder = (sameDay.maxOfOrNull { it.dayOrder } ?: 0) + 1,
      companion = sameDay.firstNotNullOfOrNull { it.companion },
    )
    val saved = draft.copy(id = dao.insert(draft))

    if (visits.isEmpty()) {
      val book = StampBook(all + saved, day)
      StampResult.New(saved, newToday = book.todayNewStamps.size, total = book.total)
    } else {
      StampResult.WelcomeBack(saved, visitCount = visits.size + 1)
    }
  }

  /** Sets (or clears, with null) the companion on every stamp of [date]. */
  suspend fun setCompanion(date: LocalDate, companion: CompanionType?) = writeLock.withLock {
    dao.setCompanionOn(date.toString(), companion?.key)
  }

  /** Saves a one-line memo (trimmed, at most [MEMO_MAX] characters); blank clears it. */
  suspend fun setMemo(stampId: Long, memo: String?) = writeLock.withLock {
    dao.setMemo(stampId, cleanMemo(memo))
  }

  companion object {
    const val MEMO_MAX = 40
    private val WHITESPACE = Regex("""\s+""")

    fun cleanMemo(memo: String?): String? =
      memo?.replace(WHITESPACE, " ")?.trim()?.take(MEMO_MAX)?.trim()?.takeIf { it.isNotEmpty() }
  }
}
