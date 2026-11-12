package com.chagokchagok.app.collection

import com.chagokchagok.app.region.RegionIndex
import kotlin.math.roundToLong

/**
 * "바다를 낀 시·군·구", derived from the bundled boundaries instead of a hand-made list.
 *
 * The regions were dissolved and simplified together (shared topology), so a border between two regions is the
 * exact same vertex pair in both. An edge used by only one region is therefore on the outer edge of the country:
 * either the coast or the northern land border (DMZ side). A region is coastal when it has outer edges and not all
 * of them are on the northern land border.
 *
 * Northern land border edge: midpoint north of [DMZ_MIN_LAT] and west of [DMZ_EAST_LON], where the DMZ meets the
 * east coast. East-coast edges further north (속초, 양양, 고성) are east of that longitude and count as coast.
 */
object CoastalRegions {
  const val DMZ_MIN_LAT = 37.85
  const val DMZ_EAST_LON = 128.36

  /** Vertex precision of the bundled data (1e-5 degrees). */
  private const val SCALE = 100_000.0

  fun derive(index: RegionIndex): Set<String> {
    val edgeUse = HashMap<Edge, Int>()
    val edgesByRegion = index.regions.associate { region ->
      val edges = ArrayList<Edge>()
      for (polygon in region.polygons) {
        for (ring in polygon.rings) {
          val n = ring.size / 2
          for (i in 0 until n) {
            val j = (i + 1) % n
            val a = point(ring[2 * i], ring[2 * i + 1])
            val b = point(ring[2 * j], ring[2 * j + 1])
            if (a == b) continue
            edges += Edge.of(a, b)
          }
        }
      }
      // A ring that repeats its first point already closes; count each distinct edge once per region.
      val distinct = edges.toSet()
      for (e in distinct) edgeUse[e] = (edgeUse[e] ?: 0) + 1
      region.code to distinct
    }
    return edgesByRegion.filter { (_, edges) ->
      val outer = edges.filter { edgeUse[it] == 1 }
      outer.isNotEmpty() && outer.any { !it.isNorthernLandBorder() }
    }.keys
  }

  private fun point(lon: Double, lat: Double): Long =
    ((lon * SCALE).roundToLong() shl 32) or ((lat * SCALE).roundToLong() and 0xFFFFFFFFL)

  private data class Edge(val a: Long, val b: Long) {
    fun isNorthernLandBorder(): Boolean {
      val midLon = (lon(a) + lon(b)) / 2
      val midLat = (lat(a) + lat(b)) / 2
      return midLat > DMZ_MIN_LAT && midLon < DMZ_EAST_LON
    }

    companion object {
      fun of(p: Long, q: Long) = if (p < q) Edge(p, q) else Edge(q, p)
      private fun lon(p: Long) = (p shr 32) / SCALE
      private fun lat(p: Long) = (p and 0xFFFFFFFFL).toInt() / SCALE
    }
  }
}
