package com.chagokchagok.app.region

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic squares at latitude 37, where 1 m east is about 0.0000112 degrees of longitude. */
class BorderCorrectionTest {
  private fun square(code: String, sido: String, name: String, west: Double, east: Double) = Region(
    code, sido, name, (west + east) / 2, 37.005,
    listOf(RegionPolygon(listOf(doubleArrayOf(west, 37.0, east, 37.0, east, 37.01, west, 37.01, west, 37.0)))),
  )

  // A | B share the border at 127.010; C is far away and also called "중구".
  private val a = square("A", "가도", "중구", 127.000, 127.010)
  private val b = square("B", "가도", "성남시", 127.010, 127.020)
  private val c = square("C", "나도", "중구", 128.000, 128.010)
  private val border = BorderCorrection(RegionIndex(listOf(a, b, c)))

  private val degPerMeterLon = 1 / (111_320.0 * kotlin.math.cos(Math.toRadians(37.005)))
  private fun westOfBorder(meters: Double) = 127.010 - meters * degPerMeterLon

  @Test fun nearBorderTheAddressPicksTheNeighbor() {
    val lon = westOfBorder(20.0)
    assertEquals(a, border.find(37.005, lon, null))
    // The navigation says we are in the neighbor's gu: its non-autonomous gu maps to the city region B.
    assertEquals(b, border.find(37.005, lon, "수정구 시흥동"))
    assertEquals(b, border.find(37.005, lon, "가도 성남시 수정구 시흥동"))
  }

  @Test fun anAddressNamingTheContainingRegionOrNoNearbyRegionKeepsIt() {
    val lon = westOfBorder(20.0)
    assertEquals(a, border.find(37.005, lon, "중구 태평동"))
    assertEquals(a, border.find(37.005, lon, "영통구 매탄동")) // 수원시 is not nearby
    assertEquals(a, border.find(37.005, lon, ""))
  }

  @Test fun farFromTheBorderTheAddressIsIgnored() {
    val lookup = border.lookup(37.005, westOfBorder(200.0))
    assertFalse(lookup.nearBorder)
    assertEquals(a, border.resolve(lookup, "수정구 시흥동"))
  }

  @Test fun toleranceIsFiftyMeters() {
    assertTrue(border.lookup(37.005, westOfBorder(45.0)).nearBorder)
    assertFalse(border.lookup(37.005, westOfBorder(60.0)).nearBorder)
  }

  @Test fun aCoastPointJustOutsideIsRecoveredOnlyWithAMatchingAddress() {
    val lon = 127.000 - 30 * degPerMeterLon // 30 m west of A, in the "sea"
    val lookup = border.lookup(37.005, lon)
    assertNull(lookup.contained)
    assertTrue(lookup.nearBorder)
    assertNull(border.resolve(lookup, null))
    assertEquals(a, border.resolve(lookup, "중구 해안동")) // the far "중구" (C) is not a candidate
  }

  @Test fun segmentDistance() {
    assertEquals(5.0, BorderCorrection.segmentToOrigin(-10.0, 5.0, 10.0, 5.0), 1e-9)
    assertEquals(5.0, BorderCorrection.segmentToOrigin(3.0, 4.0, 30.0, 40.0), 1e-9)
  }
}
