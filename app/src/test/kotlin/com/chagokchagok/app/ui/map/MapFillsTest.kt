package com.chagokchagok.app.ui.map

import com.chagokchagok.app.data.StampBook
import com.chagokchagok.app.data.StampEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MapFillsTest {
  private val today = "2026-11-12"
  private val book = StampBook(
    listOf(
      StampEntity(1, "41130", "2026-11-10", 1, companion = "family"),
      StampEntity(2, "47130", "2026-11-11", 1, companion = "friend"),
      StampEntity(3, "50110", today, 1, companion = "family"),
      StampEntity(4, "28155", today, 2, companion = "family"),
      StampEntity(5, "11110", "2026-11-09", 1),
      // Visited with family before, with friends today.
      StampEntity(6, "47130", "2026-11-10", 1, companion = "family"),
      StampEntity(7, "47130", today, 3, companion = "friend"),
    ),
    today,
  )

  @Test fun myMapFillsEveryStampedRegion() {
    assertEquals(
      mapOf(
        "41130" to Fill.BEFORE, "11110" to Fill.BEFORE,
        "47130" to Fill.TODAY, "50110" to Fill.TODAY, "28155" to Fill.TODAY,
      ),
      mapFills(book, MapFilter.MINE),
    )
  }

  @Test fun companionTabFillsOnlyThatCompanion() {
    assertEquals(
      mapOf("41130" to Fill.BEFORE, "47130" to Fill.BEFORE, "50110" to Fill.TODAY, "28155" to Fill.TODAY),
      mapFills(book, MapFilter.FAMILY),
    )
    assertEquals(mapOf("47130" to Fill.TODAY), mapFills(book, MapFilter.FRIEND))
    assertEquals(emptyMap<String, Fill>(), mapFills(book, MapFilter.PARTNER))
  }
}
