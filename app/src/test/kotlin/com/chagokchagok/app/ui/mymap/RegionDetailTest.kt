package com.chagokchagok.app.ui.mymap

import com.chagokchagok.app.data.CompanionType
import com.chagokchagok.app.data.StampBook
import com.chagokchagok.app.data.StampEntity
import com.chagokchagok.app.region.Region
import com.chagokchagok.app.region.RegionPolygon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionDetailTest {
  private val region = Region(
    "47130", "경상북도", "경주시", 129.2, 35.8,
    listOf(RegionPolygon(listOf(doubleArrayOf(129.0, 35.7, 129.4, 35.7, 129.4, 36.0, 129.0, 35.7)))),
  )

  @Test fun unstampedRegionHasNoDate() {
    val d = RegionDetail.of(region, StampBook.empty("2026-11-12"))
    assertFalse(d.stamped)
    assertNull(d.firstDate)
    assertEquals(0, d.visitCount)
    assertTrue(d.companions.isEmpty())
  }

  @Test fun collectsFirstDateVisitsCompanionsAndMemos() {
    val book = StampBook(
      listOf(
        StampEntity(1, "47130", "2026-12-22", 1, CompanionType.FRIEND.key, "  "),
        StampEntity(2, "47130", "2026-11-12", 1, CompanionType.FAMILY.key, "Bulguksa walk"),
        StampEntity(3, "47130", "2026-11-13", 1, CompanionType.FAMILY.key, "Hwangnidan-gil"),
        StampEntity(4, "36110", "2026-11-12", 2),
      ),
      "2026-12-22",
    )
    val d = RegionDetail.of(region, book)
    assertEquals("2026-11-12", d.firstDate)
    assertEquals(3, d.visitCount)
    assertEquals(listOf(CompanionType.FAMILY, CompanionType.FRIEND), d.companions)
    // Newest first; blank memos are skipped.
    assertEquals(listOf("Hwangnidan-gil", "Bulguksa walk"), d.memos)
  }
}
