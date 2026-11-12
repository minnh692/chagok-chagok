package com.chagokchagok.app.collection

import com.chagokchagok.app.data.StampBook
import com.chagokchagok.app.region.Region
import com.chagokchagok.app.region.RegionIndex
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** One recommended next stamp: an unstamped region and the collections a stamp there would advance. */
data class NextStamp(val region: Region, val km: Double, val adjacent: Boolean, val advances: List<CollectionProgress>)

/**
 * "다음 도장 추천": the nearest unstamped regions from where the user is (or last stamped).
 * Regions bordering the starting region come first, then the rest; each group is sorted by the
 * straight-line distance between label points. No external data (no tourism API).
 */
class NextStamps(private val index: RegionIndex, private val collections: Collections) {
  /** Region code -> codes of regions sharing a border with it. */
  val neighbors: Map<String, Set<String>> = deriveNeighbors(index)

  /** The most recent stamp's region: latest date, then latest order within that day. */
  fun anchor(book: StampBook): String? =
    book.stamps.maxWithOrNull(compareBy({ it.date }, { it.dayOrder }))?.regionCode

  fun recommend(from: String, book: StampBook, count: Int = 3): List<NextStamp> {
    val start = index[from] ?: return emptyList()
    val near = neighbors[from].orEmpty()
    return index.regions.asSequence()
      .filter { it.code != from && it.code !in book.stampedCodes }
      .map { NextStamp(it, km(start, it), it.code in near, emptyList()) }
      .sortedWith(compareBy<NextStamp> { !it.adjacent }.thenBy { it.km })
      .take(count)
      .map { it.copy(advances = collections.advancedBy(it.region.code, book)) }
      .toList()
  }

  companion object {
    private const val EARTH_KM = 6371.0
    private const val SCALE = 100_000.0

    /** Great-circle distance between two regions' label points. */
    fun km(a: Region, b: Region): Double {
      val lat1 = Math.toRadians(a.labelLat)
      val lat2 = Math.toRadians(b.labelLat)
      val dLat = lat2 - lat1
      val dLon = Math.toRadians(b.labelLon - a.labelLon)
      val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
      return 2 * EARTH_KM * asin(sqrt(h))
    }

    /**
     * Two regions are neighbors when they share a boundary edge. The bundled boundaries share topology
     * (see [CoastalRegions]), so a shared border is the same vertex pair in both regions.
     */
    fun deriveNeighbors(index: RegionIndex): Map<String, Set<String>> {
      val owners = HashMap<Pair<Long, Long>, MutableSet<String>>()
      for (region in index.regions) {
        for (polygon in region.polygons) {
          for (ring in polygon.rings) {
            val n = ring.size / 2
            for (i in 0 until n) {
              val j = (i + 1) % n
              val a = point(ring[2 * i], ring[2 * i + 1])
              val b = point(ring[2 * j], ring[2 * j + 1])
              if (a == b) continue
              owners.getOrPut(if (a < b) a to b else b to a) { HashSet() } += region.code
            }
          }
        }
      }
      val result = HashMap<String, MutableSet<String>>()
      for (codes in owners.values) {
        if (codes.size < 2) continue
        for (c in codes) result.getOrPut(c) { HashSet() } += codes - c
      }
      return result
    }

    private fun point(lon: Double, lat: Double): Long =
      ((lon * SCALE).roundToLong() shl 32) or ((lat * SCALE).roundToLong() and 0xFFFFFFFFL)
  }
}
