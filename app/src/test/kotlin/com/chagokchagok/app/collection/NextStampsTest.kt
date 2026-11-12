package com.chagokchagok.app.collection

import com.chagokchagok.app.data.StampBook
import com.chagokchagok.app.data.StampEntity
import com.chagokchagok.app.region.Region
import com.chagokchagok.app.region.RegionIndex
import com.chagokchagok.app.region.RegionPolygon
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun box(code: String, lon: Double, lat: Double, w: Double = 0.1, h: Double = 0.1) = Region(
  code, "X도", "R$code", lon + w / 2, lat + h / 2,
  listOf(RegionPolygon(listOf(doubleArrayOf(lon, lat, lon + w, lat, lon + w, lat + h, lon, lat + h, lon, lat)))),
)

class NextStampsTest {
  // A row of squares 1..5 (west to east) and a lone island "far" just south of square 1, not touching it.
  private val index = RegionIndex(
    listOf(box("1", 127.0, 36.0), box("2", 127.1, 36.0), box("3", 127.2, 36.0), box("4", 127.3, 36.0), box("5", 127.4, 36.0),
      box("far", 127.0, 35.95, 0.02, 0.02)),
  )
  private val next = NextStamps(index, Collections(index, CoastalRegions.derive(index)))

  @Test fun neighborsShareAnEdge() {
    assertEquals(setOf("1", "3"), next.neighbors["2"])
    assertEquals(setOf("2"), next.neighbors["1"])
    assertTrue(next.neighbors["far"].isNullOrEmpty())
  }

  @Test fun adjacentUnstampedRegionsComeFirstThenByDistance() {
    val book = StampBook(listOf(StampEntity(regionCode = "1", date = "2026-11-12", dayOrder = 1)), "2026-11-12")
    // From 1: neighbor 2 first; then "far" (~11 km) before 3 (~18 km).
    assertEquals(listOf("2", "far", "3"), next.recommend("1", book).map { it.region.code })
    assertTrue(next.recommend("1", book).first().adjacent)
  }

  @Test fun stampedRegionsAreSkippedAndAdvancesAreListed() {
    val book = StampBook(
      listOf(StampEntity(regionCode = "1", date = "2026-11-11", dayOrder = 1), StampEntity(regionCode = "2", date = "2026-11-12", dayOrder = 1)),
      "2026-11-12",
    )
    assertEquals("2", next.anchor(book))
    val list = next.recommend("2", book)
    assertEquals(listOf("3", "far", "4"), list.map { it.region.code })
    assertTrue(list.first().advances.any { it.id == "sido:X도" })
  }

  @Test fun realDataNeighbors() {
    val real = RegionIndex.fromGeoJson(File("src/main/assets/regions/sgg230.geojson").readText(Charsets.UTF_8))
    val n = NextStamps.deriveNeighbors(real)
    // 세종 borders 대전 유성구 and 공주시; Jeju has no land neighbor.
    val sejong = real.regions.first { it.sido.startsWith("세종") }.code
    val names = n[sejong].orEmpty().mapNotNull { real[it]?.name }
    assertTrue(names.toString(), "공주시" in names && "유성구" in names)
    assertTrue(real.regions.filter { it.sido.startsWith("제주") }.all { n[it.code].orEmpty().all { c -> real[c]!!.sido.startsWith("제주") } })
  }
}
