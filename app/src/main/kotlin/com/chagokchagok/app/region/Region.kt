package com.chagokchagok.app.region

/** Axis-aligned bounds in degrees. */
class Bounds(val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double) {
  fun contains(lon: Double, lat: Double) = lon >= minLon && lon <= maxLon && lat >= minLat && lat <= maxLat

  companion object {
    fun of(rings: List<DoubleArray>): Bounds {
      var minLon = Double.POSITIVE_INFINITY
      var minLat = Double.POSITIVE_INFINITY
      var maxLon = Double.NEGATIVE_INFINITY
      var maxLat = Double.NEGATIVE_INFINITY
      for (ring in rings) {
        var i = 0
        while (i < ring.size) {
          val lon = ring[i]
          val lat = ring[i + 1]
          if (lon < minLon) minLon = lon
          if (lon > maxLon) maxLon = lon
          if (lat < minLat) minLat = lat
          if (lat > maxLat) maxLat = lat
          i += 2
        }
      }
      return Bounds(minLon, minLat, maxLon, maxLat)
    }

    fun union(all: List<Bounds>) = Bounds(
      all.minOf { it.minLon },
      all.minOf { it.minLat },
      all.maxOf { it.maxLon },
      all.maxOf { it.maxLat },
    )
  }
}

/**
 * One polygon of a region: the first ring is the outline, any further rings are holes.
 * Each ring is a flat array of lon, lat, lon, lat, ...
 */
class RegionPolygon(val rings: List<DoubleArray>) {
  val bounds: Bounds = Bounds.of(rings)

  /** Even-odd test over all rings, so points inside a hole are outside. */
  fun contains(lon: Double, lat: Double): Boolean {
    if (!bounds.contains(lon, lat)) return false
    var inside = false
    for (ring in rings) {
      val n = ring.size
      if (n < 6) continue
      var jLon = ring[n - 2]
      var jLat = ring[n - 1]
      var i = 0
      while (i < n) {
        val iLon = ring[i]
        val iLat = ring[i + 1]
        if ((iLat > lat) != (jLat > lat) && lon < (jLon - iLon) * (lat - iLat) / (jLat - iLat) + iLon) {
          inside = !inside
        }
        jLon = iLon
        jLat = iLat
        i += 2
      }
    }
    return inside
  }
}

/** One stampable si/gun/gu. [sido] and [name] are shown exactly as they appear in the bundled data. */
class Region(
  val code: String,
  val sido: String,
  val name: String,
  val labelLon: Double,
  val labelLat: Double,
  val polygons: List<RegionPolygon>,
) {
  val bounds: Bounds = Bounds.union(polygons.map { it.bounds })
  val fullName: String get() = "$sido $name"

  fun contains(lon: Double, lat: Double): Boolean =
    bounds.contains(lon, lat) && polygons.any { it.contains(lon, lat) }

  override fun toString() = "Region($code $fullName)"
}
