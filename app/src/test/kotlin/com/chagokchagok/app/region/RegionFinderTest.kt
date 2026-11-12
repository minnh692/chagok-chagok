package com.chagokchagok.app.region

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RegionFinderTest {
  private fun region(lat: Double, lon: Double): Region =
    finder.find(lat, lon) ?: run {
      fail("no region at ($lat, $lon)")
      error("unreachable")
    }

  @Test fun loadsAll230Regions() {
    assertEquals(230, index.size)
    assertEquals(230, index.regions.map { it.code }.toSet().size)
  }

  @Test fun naviEmulatorPositionIsSeongnam() {
    val r = region(37.41272, 127.09468)
    assertEquals("경기도 성남시", r.fullName)
    assertEquals("41130", r.code)
  }

  @Test fun gyeongju() {
    assertEquals("경상북도 경주시", region(35.8562, 129.2247).fullName)
  }

  @Test fun jejuSi() {
    val r = region(33.4996, 126.5312)
    assertEquals("제주시", r.name)
    assertEquals("제주특별자치도", r.sido)
  }

  @Test fun yeongjongGu() {
    assertEquals("인천광역시 영종구", region(37.4602, 126.4407).fullName)
  }

  @Test fun gwangjuAndJeonnamUseTheSidoNameFromTheData() {
    val merged = index.regions.filter { it.code.startsWith("12") }
    assertEquals(setOf("전남광주통합특별시"), merged.map { it.sido }.toSet())
    assertEquals(27, merged.size)
  }

  @Test fun everyLabelPointIsInsideItsOwnRegion() {
    for (r in index.regions) {
      assertEquals("label of ${r.fullName}", r.code, finder.find(r.labelLat, r.labelLon)?.code)
    }
  }

  @Test fun openSeaAndAbroadHaveNoRegion() {
    assertNull(finder.find(37.0, 131.0)) // East Sea
    assertNull(finder.find(35.6812, 139.7671)) // Tokyo
  }

  @Test fun lookupByCode() {
    assertEquals("성남시", index["41130"]?.name)
    assertNull(index["00000"])
    assertTrue(index.regions.all { index[it.code] === it })
  }

  @Test fun parserHandlesHolesEscapesAndUnknownMembers() {
    val json = """
      {"type":"FeatureCollection","name":"t","features":[
        {"type":"Feature","id":7,"bbox":[0,0,10,10],
         "properties":{"code":"99999","sido":"AB","name":"x\"y","label_lon":1.0,"label_lat":1.0,"extra":null},
         "geometry":{"coordinates":[[[0,0],[10,0],[10,10],[0,10],[0,0]],[[4,4],[6,4],[6,6],[4,6],[4,4]]],"type":"Polygon"}}
      ]}
    """.trimIndent()
    val r = GeoJsonRegionParser.parse(json).single()
    assertEquals("AB", r.sido)
    assertEquals("x\"y", r.name)
    assertTrue(r.contains(1.0, 1.0))
    assertTrue(!r.contains(5.0, 5.0)) // inside the hole
    assertTrue(!r.contains(11.0, 5.0))
  }

  companion object {
    // Unit tests run with the module directory (app/) as the working directory.
    private val index: RegionIndex by lazy {
      RegionIndex.fromGeoJson(File("src/main/assets/regions/sgg230.geojson").readText(Charsets.UTF_8))
    }
    private val finder by lazy { RegionFinder(index) }
  }
}
