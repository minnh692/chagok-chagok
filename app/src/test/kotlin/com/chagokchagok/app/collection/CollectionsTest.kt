package com.chagokchagok.app.collection

import com.chagokchagok.app.data.CompanionType
import com.chagokchagok.app.data.StampBook
import com.chagokchagok.app.data.StampEntity
import com.chagokchagok.app.region.Region
import com.chagokchagok.app.region.RegionIndex
import com.chagokchagok.app.region.RegionPolygon
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A unit square with its lower-left corner at (lon, lat). */
private fun square(code: String, sido: String, lon: Double, lat: Double, size: Double = 0.1) = Region(
  code, sido, "R$code", lon + size / 2, lat + size / 2,
  listOf(RegionPolygon(listOf(doubleArrayOf(lon, lat, lon + size, lat, lon + size, lat + size, lon, lat + size, lon, lat)))),
)

private fun stamp(code: String, date: String, companion: CompanionType? = null, order: Int = 1) =
  StampEntity(regionCode = code, date = date, dayOrder = order, companion = companion?.key)

class CollectionsTest {
  // 3x3 grid in the south (no DMZ edges): the middle square "5" is the only one without outer edges.
  private val grid = RegionIndex(
    (0 until 9).map { i -> square("${i + 1}", if (i < 3) "A도" else "B도", 127.0 + (i % 3) * 0.1, 35.0 + (i / 3) * 0.1) },
  )

  @Test fun coastIsRegionsWithUnsharedEdges() {
    assertEquals(setOf("1", "2", "3", "4", "6", "7", "8", "9"), CoastalRegions.derive(grid))
  }

  @Test fun regionWhoseOnlyOuterEdgesAreOnTheNorthernLandBorderIsNotCoastal() {
    // Column of two: "S" south of "N". N's top edge is north of 37.85 and west of 128.36 (DMZ side).
    // Both sit between two inland neighbors so only top/bottom matter.
    val index = RegionIndex(
      listOf(
        square("W1", "X", 127.0, 37.80), square("N", "X", 127.1, 37.80), square("E1", "X", 127.2, 37.80),
        square("W2", "X", 127.0, 37.70), square("S", "X", 127.1, 37.70), square("E2", "X", 127.2, 37.70),
        square("B1", "X", 127.0, 37.60), square("B2", "X", 127.1, 37.60), square("B3", "X", 127.2, 37.60),
      ),
    )
    val coast = CoastalRegions.derive(index)
    // N's only outer edge is its top (lat 37.90, DMZ side) -> not coastal; the middle "S" has no outer edges.
    assertFalse("N" in coast)
    assertFalse("S" in coast)
    // B2's bottom edge (lat 37.60) is south of the DMZ band -> coastal.
    assertTrue("B2" in coast)
  }

  @Test fun eastCoastNorthOfTheDmzLatitudeStillCountsAsCoast() {
    val index = RegionIndex(listOf(square("E", "X", 128.5, 38.1)))
    assertEquals(setOf("E"), CoastalRegions.derive(index))
  }

  @Test fun realDataCoastList() {
    val coast = CoastalRegions.derive(realIndex)
    assertEquals(75, coast.size)
    // Inland DMZ counties are excluded; east-coast 고성/속초 and the west-coast islands are in.
    for (code in listOf("41800", "51780", "51790", "51800", "51810")) assertFalse(code, code in coast)
    for (code in listOf("51820", "51210", "28710", "28720", "47940", "50110", "26200")) assertTrue(code, code in coast)
    // Fully inland examples.
    for (code in listOf("41130", "11110", "43110")) assertFalse(code, code in coast)
  }

  @Test fun sidoCompletesWhenEveryRegionIsStamped() {
    val c = Collections(grid, CoastalRegions.derive(grid))
    val partial = StampBook(listOf(stamp("1", "2026-11-01"), stamp("2", "2026-11-01", order = 2)), "2026-11-01")
    val a = c.progress(partial).first { it.id == "sido:A도" }
    assertEquals(2, a.done); assertEquals(3, a.target); assertFalse(a.complete)
    val full = StampBook(partial.stamps + stamp("3", "2026-11-02"), "2026-11-02")
    assertTrue(c.progress(full).first { it.id == "sido:A도" }.complete)
    assertTrue("sido:A도" in c.completedIds(full))
    assertFalse("sido:B도" in c.completedIds(full))
  }

  @Test fun realDataHas16SidoSetsWithGwangjuJeonnamAsOne() {
    val c = Collections(realIndex, emptySet())
    // 17 traditional sidos, with 광주 and 전남 merged into one in this data version.
    assertEquals(16, c.sidos.size)
    assertEquals(27, c.progress(StampBook.empty("2026-11-12")).first { it.id == "sido:전남광주통합특별시" }.target)
  }

  @Test fun coastCompletesWhenAllCoastalRegionsAreStamped() {
    val c = Collections(grid, CoastalRegions.derive(grid))
    val all = listOf("1", "2", "3", "4", "6", "7", "8", "9").mapIndexed { i, code -> stamp(code, "2026-10-%02d".format(i + 1)) }
    val book = StampBook(all, "2026-10-09")
    val coast = c.progress(book).first { it.id == Collections.COAST_ID }
    assertEquals(8, coast.done); assertTrue(coast.complete)
    val missingOne = StampBook(all.drop(1), "2026-10-09")
    assertFalse(c.progress(missingOne).first { it.id == Collections.COAST_ID }.complete)
  }

  @Test fun seasonNeedsTheSameRegionInTwoDifferentSeasons() {
    val c = Collections(grid, emptySet())
    // Two visits in autumn (Sep, Nov): one season only.
    val sameSeason = StampBook(listOf(stamp("1", "2026-09-10"), stamp("1", "2026-11-20")), "2026-11-20")
    assertTrue(c.twoSeasonRegions(sameSeason).isEmpty())
    // Autumn + winter (Dec): counts once.
    val twoSeasons = StampBook(sameSeason.stamps + stamp("1", "2026-12-01"), "2026-12-01")
    assertEquals(setOf("1"), c.twoSeasonRegions(twoSeasons))
    assertTrue("season:1" in c.completedIds(twoSeasons))
    // Different regions in different seasons do not count.
    val split = StampBook(listOf(stamp("1", "2026-04-01"), stamp("2", "2026-07-01")), "2026-07-01")
    assertTrue(c.twoSeasonRegions(split).isEmpty())
    assertEquals(Season.WINTER, Season.of(java.time.LocalDate.of(2027, 2, 28)))
  }

  @Test fun companionTargetsAreTenFiveFive() {
    val c = Collections(grid, emptySet())
    val family = (1..9).map { stamp("$it", "2026-11-%02d".format(it), CompanionType.FAMILY) }
    val partner = (1..5).map { stamp("$it", "2026-10-%02d".format(it), CompanionType.PARTNER) }
    val book = StampBook(family + partner, "2026-11-09")
    val p = c.progress(book).associateBy { it.id }
    assertEquals(9, p.getValue("companion:family").done); assertEquals(10, p.getValue("companion:family").target)
    assertFalse(p.getValue("companion:family").complete)
    assertTrue(p.getValue("companion:partner").complete)
    assertEquals(0, p.getValue("companion:friend").done); assertEquals(5, p.getValue("companion:friend").target)
    assertEquals("가족과 함께", p.getValue("companion:family").title)
    assertEquals("연인과 함께", p.getValue("companion:partner").title)
    assertEquals("친구와 함께", p.getValue("companion:friend").title)
  }

  private companion object {
    val realIndex: RegionIndex by lazy {
      RegionIndex.fromGeoJson(File("src/main/assets/regions/sgg230.geojson").readText(Charsets.UTF_8))
    }
  }
}
