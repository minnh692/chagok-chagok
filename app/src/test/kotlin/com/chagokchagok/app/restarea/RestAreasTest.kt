package com.chagokchagok.app.restarea

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestAreasTest {
  private val csv = """
    name,route_no,route_name,direction,lat,lon
    안성(서울),1,경부선,상행,37.0000,127.2000
    안성(부산),1,경부선,하행,36.9500,127.2100
    여주,50,영동선,양방향,37.3000,127.6000
    안성(인천),400,수도권제2순환선,상행,37.5000,126.7000
  """.trimIndent()

  private val areas = RestAreaCatalog.merge(RestAreaCatalog.parse(csv))
  private val finder = RestAreaFinder(areas)

  // Offsets north of a point: 1 m of latitude is about 1 / 111_195 degrees.
  private fun north(lat: Double, meters: Double) = lat + meters / 111_195.0

  @Test fun insideThreeHundredMetersIsFound() {
    val hit = finder.find(north(37.3, 250.0), 127.6)
    assertEquals("50:여주", hit?.id)
    assertNotNull(finder.find(37.3, 127.6))
  }

  @Test fun outsideThreeHundredMetersIsNotFound() {
    assertNull(finder.find(north(37.3, 350.0), 127.6))
    assertNull(finder.find(36.0, 128.0))
  }

  @Test fun upAndDownSidesAreOneItem() {
    // Same route and name -> one rest area with both sides; the same name on another route stays separate.
    assertEquals(3, areas.size)
    val anseong = areas.single { it.id == "1:안성" }
    assertEquals(2, anseong.points.size)
    assertEquals("안성", anseong.name)
    // Collecting at either side gives the same id, so a visit to both counts once.
    val up = finder.find(37.0, 127.2)!!
    val down = finder.find(36.95, 127.21)!!
    assertEquals(up.id, down.id)
    assertEquals(1, setOf(up.id, down.id).size)
  }

  @Test fun bundledDataParsesAndMerges() {
    val points = RestAreaCatalog.parse(File("src/main/assets/${RestAreaCatalog.ASSET_PATH}").readText(Charsets.UTF_8))
    assertEquals(215, points.size)
    val merged = RestAreaCatalog.merge(points)
    assertEquals(133, merged.size)
    assertTrue(merged.all { it.points.size in 1..2 })
    assertTrue(points.all { it.lat in 33.0..39.0 && it.lon in 124.0..132.0 })
  }
}
