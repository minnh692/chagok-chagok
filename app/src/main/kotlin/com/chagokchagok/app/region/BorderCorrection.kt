package com.chagokchagok.app.region

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Border correction (K-1). The bundled borders are simplified and can be off by tens of meters, so a car parked
 * right at a si/gun/gu border may land in the neighbor (or in the sea on a coastline). When the point is within
 * [toleranceMeters] of a border, the navigation's address for the current position decides between the regions
 * that are that close. Without an address, or when it names none of them, the polygon result stands.
 */
class BorderCorrection(private val index: RegionIndex, private val toleranceMeters: Double = TOLERANCE_METERS) {
  private val finder = RegionFinder(index)

  /** The polygon result plus every other region whose border is within the tolerance. */
  class Lookup(val contained: Region?, val nearby: List<Region>) {
    /** True when the address is worth asking for: another region (or the coast) is within the tolerance. */
    val nearBorder: Boolean get() = nearby.any { it != contained }
  }

  fun lookup(latitude: Double, longitude: Double): Lookup {
    val contained = finder.find(latitude, longitude)
    val mPerLat = METERS_PER_DEGREE
    val mPerLon = METERS_PER_DEGREE * cos(Math.toRadians(latitude))
    val dLat = toleranceMeters / mPerLat
    val dLon = toleranceMeters / mPerLon
    val nearby = index.regions.filter { r ->
      if (r == contained) return@filter false
      val b = r.bounds
      if (longitude < b.minLon - dLon || longitude > b.maxLon + dLon || latitude < b.minLat - dLat || latitude > b.maxLat + dLat) {
        return@filter false
      }
      distanceMeters(r, longitude, latitude, mPerLon, mPerLat) <= toleranceMeters
    }
    // The contained region is always a candidate: an address naming it keeps it.
    return Lookup(contained, listOfNotNull(contained) + nearby)
  }

  /**
   * Picks the region: [address] (from the navigation, e.g. "수정구 시흥동" or "울산광역시 중구 성안동") may name one
   * of the nearby regions, by its own name or by a non-autonomous gu of a city that is stamped as one region.
   */
  fun resolve(lookup: Lookup, address: String?): Region? {
    if (address.isNullOrBlank() || !lookup.nearBorder) return lookup.contained
    val tokens = address.split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }
    val named = lookup.nearby.filter { r -> tokens.any { t -> t == r.name || r.name in CITY_OF_GU[t].orEmpty() } }
    return when {
      named.isEmpty() -> lookup.contained
      lookup.contained in named -> lookup.contained
      // Several neighbors with the same short name (e.g. two "중구") cannot be within 50 m of one point;
      // if they ever are, prefer the one whose sido is named too.
      else -> named.firstOrNull { r -> tokens.any { it == r.sido } } ?: named.first()
    }
  }

  fun find(latitude: Double, longitude: Double, address: String?): Region? = resolve(lookup(latitude, longitude), address)

  private fun distanceMeters(region: Region, lon: Double, lat: Double, mPerLon: Double, mPerLat: Double): Double {
    var best = Double.POSITIVE_INFINITY
    for (polygon in region.polygons) {
      for (ring in polygon.rings) {
        val n = ring.size
        if (n < 4) continue
        var jx = (ring[n - 2] - lon) * mPerLon
        var jy = (ring[n - 1] - lat) * mPerLat
        var i = 0
        while (i < n) {
          val ix = (ring[i] - lon) * mPerLon
          val iy = (ring[i + 1] - lat) * mPerLat
          best = min(best, segmentToOrigin(jx, jy, ix, iy))
          jx = ix
          jy = iy
          i += 2
        }
      }
    }
    return best
  }

  companion object {
    const val TOLERANCE_METERS = 50.0
    private const val METERS_PER_DEGREE = 111_320.0

    /** Distance from (0, 0) to the segment a-b, in the segment's units. */
    internal fun segmentToOrigin(ax: Double, ay: Double, bx: Double, by: Double): Double {
      val dx = bx - ax
      val dy = by - ay
      val len2 = dx * dx + dy * dy
      val t = if (len2 == 0.0) 0.0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / len2))
      val px = ax + t * dx
      val py = ay + t * dy
      return kotlin.math.sqrt(px * px + py * py)
    }

    /**
     * Non-autonomous gu (일반구) -> the city that is one region in the bundled data. The navigation address
     * names the gu, so these map it back. Short names shared with autonomous gu ("남구", "북구") are safe because
     * only regions within the tolerance are considered.
     */
    val CITY_OF_GU: Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
      fun city(name: String, vararg gu: String) = gu.forEach { getOrPut(it) { mutableSetOf() } += name }
      city("수원시", "장안구", "권선구", "팔달구", "영통구")
      city("성남시", "수정구", "중원구", "분당구")
      city("안양시", "만안구", "동안구")
      city("부천시", "원미구", "소사구", "오정구")
      city("안산시", "상록구", "단원구")
      city("고양시", "덕양구", "일산동구", "일산서구")
      city("용인시", "처인구", "기흥구", "수지구")
      city("화성시", "만세구", "효행구", "병점구", "동탄구")
      city("청주시", "상당구", "서원구", "흥덕구", "청원구")
      city("천안시", "동남구", "서북구")
      city("전주시", "완산구", "덕진구")
      city("포항시", "남구", "북구")
      city("창원시", "의창구", "성산구", "마산합포구", "마산회원구", "진해구")
    }
  }
}
