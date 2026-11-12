package com.chagokchagok.app.data

import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory stand-in for the Room DAO, enforcing the same unique (region, date) rule. */
private class FakeStampDao : StampDao {
  val rows = MutableStateFlow<List<StampEntity>>(emptyList())
  private var nextId = 1L

  override fun observeAll(): Flow<List<StampEntity>> = rows
  override suspend fun getAll() = rows.value.sortedWith(compareBy({ it.date }, { it.dayOrder }))
  override suspend fun insert(stamp: StampEntity): Long {
    check(rows.value.none { it.regionCode == stamp.regionCode && it.date == stamp.date }) { "unique violation" }
    val id = nextId++
    rows.value = rows.value + stamp.copy(id = id)
    return id
  }
  override suspend fun setCompanionOn(date: String, companion: String?) {
    rows.value = rows.value.map { if (it.date == date) it.copy(companion = companion) else it }
  }
  override suspend fun setMemo(id: Long, memo: String?) {
    rows.value = rows.value.map { if (it.id == id) it.copy(memo = memo) else it }
  }
}

class StampRepositoryTest {
  private val dao = FakeStampDao()
  private val repo = StampRepository(dao)
  private val day1 = LocalDate.of(2026, 11, 12)
  private val day2 = day1.plusDays(1)
  private val seongnam = "41130"
  private val gyeongju = "47130"
  private val jeju = "50110"

  private fun book(day: LocalDate) = StampBook(dao.rows.value, day.toString())

  @Test fun newRegionIsStampedAndCounted() = runBlocking {
    val result = repo.stamp(seongnam, day1)
    assertTrue(result is StampResult.New)
    result as StampResult.New
    assertEquals(1, result.total)
    assertEquals(1, result.newToday)
    assertEquals(StampEntity(id = 1, regionCode = seongnam, date = "2026-11-12", dayOrder = 1), result.stamp)
    assertEquals(1, book(day1).total)
    assertEquals(setOf(seongnam), book(day1).todayCodes)
  }

  @Test fun sameRegionSameDayAddsNothing() = runBlocking {
    val first = repo.stamp(seongnam, day1)
    val again = repo.stamp(seongnam, day1)
    assertTrue(again is StampResult.AlreadyStamped)
    assertEquals(first.stamp, again.stamp)
    assertEquals(1, dao.rows.value.size)
    assertEquals(1, book(day1).total)
    assertEquals(1, book(day1).visitCount(seongnam))
  }

  @Test fun sameRegionOnAnotherDayIsAWelcomeBackVisit() = runBlocking {
    repo.stamp(seongnam, day1)
    val result = repo.stamp(seongnam, day2)
    assertTrue(result is StampResult.WelcomeBack)
    assertEquals(2, (result as StampResult.WelcomeBack).visitCount)
    assertEquals(1, book(day2).total) // total unchanged
    assertEquals(2, book(day2).visitCount(seongnam))
    assertTrue(book(day2).todayNewStamps.isEmpty()) // visited today, but not new
    assertEquals(setOf(seongnam), book(day2).todayCodes)
    // and a third open on day 2 changes nothing
    assertTrue(repo.stamp(seongnam, day2) is StampResult.AlreadyStamped)
    assertEquals(2, book(day2).visitCount(seongnam))
  }

  @Test fun orderWithinADayCountsUpAndRestartsEachDay() = runBlocking {
    assertEquals(1, repo.stamp(seongnam, day1).stamp.dayOrder)
    assertEquals(2, repo.stamp(gyeongju, day1).stamp.dayOrder)
    repo.stamp(seongnam, day1) // already stamped: must not consume an order number
    val third = repo.stamp(jeju, day1) as StampResult.New
    assertEquals(3, third.stamp.dayOrder)
    assertEquals(3, third.newToday)
    assertEquals(3, third.total)

    assertEquals(1, repo.stamp(jeju, day2).stamp.dayOrder) // revisit starts day 2 at 1
    val incheon = repo.stamp("28155", day2) as StampResult.New
    assertEquals(2, incheon.stamp.dayOrder)
    assertEquals(1, incheon.newToday) // only one NEW region on day 2
    assertEquals(4, incheon.total)
    assertEquals(listOf(seongnam, gyeongju, jeju), book(day1).todayStamps.map { it.regionCode })
    assertEquals(listOf(jeju, "28155"), book(day2).todayStamps.map { it.regionCode })
  }

  @Test fun companionIsSavedOnTodaysStampsOnly() = runBlocking {
    repo.stamp(seongnam, day1)
    repo.stamp(gyeongju, day2)
    repo.stamp(jeju, day2)
    assertNull(book(day2).todayCompanion)

    repo.setCompanion(day2, CompanionType.FAMILY)
    assertEquals(CompanionType.FAMILY, book(day2).todayCompanion)
    assertEquals(setOf(gyeongju, jeju), book(day2).codesWith(CompanionType.FAMILY))
    assertTrue(book(day2).codesWith(CompanionType.FRIEND).isEmpty())
    assertNull(dao.rows.value.single { it.regionCode == seongnam }.companion)

    // a later stamp on the same day takes the day's companion
    assertEquals("family", repo.stamp("28155", day2).stamp.companion)

    repo.setCompanion(day2, CompanionType.FRIEND)
    assertEquals(setOf(gyeongju, jeju, "28155"), book(day2).codesWith(CompanionType.FRIEND))
    assertTrue(book(day2).codesWith(CompanionType.FAMILY).isEmpty())

    repo.setCompanion(day2, null)
    assertNull(book(day2).todayCompanion)
  }

  @Test fun memoIsTrimmedAndBlankClearsIt() = runBlocking {
    val id = repo.stamp(seongnam, day1).stamp.id
    repo.setMemo(id, "  첫 도장  ")
    assertEquals("첫 도장", dao.rows.value.single().memo)
    repo.setMemo(id, "   ")
    assertNull(dao.rows.value.single().memo)
  }

  @Test fun emptyBookShowsZero() {
    val book = StampBook.empty("2026-11-12")
    assertEquals(0, book.total)
    assertTrue(book.stampedCodes.isEmpty() && book.todayStamps.isEmpty())
    assertEquals(0, book.visitCount(seongnam))
  }

  /** Guards the privacy rule on the real Room schema: only these columns may exist. */
  @Test fun exportedSchemaHasNoCoordinateOrTimeColumns() {
    val dir = File("schemas/com.chagokchagok.app.data.AppDatabase")
    val latest = dir.listFiles { f -> f.extension == "json" }?.maxByOrNull { it.nameWithoutExtension.toInt() }
    requireNotNull(latest) { "no exported Room schema in $dir (build the app once)" }
    val text = latest.readText()
    val tables = Regex("\"tableName\":\\s*\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toSet()
    val columns = Regex("\"columnName\":\\s*\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toSet()
    assertEquals(setOf("stamps"), tables)
    assertEquals(setOf("id", "region_code", "date", "day_order", "companion", "memo"), columns)
  }

  @Test fun memoIsTrimmedCappedAndClearable() = runBlocking {
    val stamp = repo.stamp(seongnam, day1).stamp
    repo.setMemo(stamp.id, "  두물머리   산책  ")
    assertEquals("두물머리 산책", dao.rows.value.single().memo)
    repo.setMemo(stamp.id, "가".repeat(60))
    assertEquals(StampRepository.MEMO_MAX, dao.rows.value.single().memo!!.length)
    repo.setMemo(stamp.id, "   ")
    assertNull(dao.rows.value.single().memo)
  }
}
