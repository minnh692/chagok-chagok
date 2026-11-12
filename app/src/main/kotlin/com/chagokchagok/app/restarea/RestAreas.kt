package com.chagokchagok.app.restarea

import android.content.Context
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One row of the bundled data: a rest area on one side of an expressway. */
data class RestAreaPoint(
  val name: String,
  val routeNo: String,
  val routeName: String,
  val direction: String,
  val lat: Double,
  val lon: Double,
)

/**
 * One collectible rest area. The up and down sides of the same rest area ("안성(서울)" / "안성(부산)" on route 1)
 * are one item: same route number and the same name without the direction in parentheses.
 */
data class RestArea(val id: String, val name: String, val routeName: String, val points: List<RestAreaPoint>)

object RestAreaCatalog {
  const val ASSET_PATH = "restareas/rest_areas.csv"

  private val DIRECTION_SUFFIX = Regex("""\s*\([^)]*\)\s*$""")

  @Volatile private var cached: List<RestArea>? = null

  suspend fun load(context: Context): List<RestArea> = cached ?: withContext(Dispatchers.IO) {
    val text = context.applicationContext.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
    merge(parse(text)).also { cached = it }
  }

  /** Parses the bundled CSV: name,route_no,route_name,direction,lat,lon (no quoted commas in this data). */
  fun parse(csv: String): List<RestAreaPoint> =
    csv.removePrefix("﻿").lineSequence().drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
      val f = line.split(',')
      if (f.size < 6) return@mapNotNull null
      val lat = f[4].trim().toDoubleOrNull() ?: return@mapNotNull null
      val lon = f[5].trim().toDoubleOrNull() ?: return@mapNotNull null
      RestAreaPoint(f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim(), lat, lon)
    }.toList()

  fun baseName(name: String): String = name.replace(DIRECTION_SUFFIX, "").trim()

  /** Merges both directions of a rest area into one item; the id is "<route no>:<base name>". */
  fun merge(points: List<RestAreaPoint>): List<RestArea> =
    points.groupBy { "${it.routeNo}:${baseName(it.name)}" }
      .map { (id, group) -> RestArea(id, baseName(group.first().name), group.first().routeName, group) }
}

/** Finds the rest area within [radiusMeters] of a point (nearest wins). */
class RestAreaFinder(private val areas: List<RestArea>, private val radiusMeters: Double = RADIUS_METERS) {
  fun find(lat: Double, lon: Double): RestArea? {
    // About 0.01 degrees of latitude per km; skip far points before the exact distance.
    val box = radiusMeters / 1000.0 * 0.0135
    var best: RestArea? = null
    var bestM = Double.MAX_VALUE
    for (area in areas) {
      for (p in area.points) {
        if (kotlin.math.abs(p.lat - lat) > box || kotlin.math.abs(p.lon - lon) > box * 1.4) continue
        val m = meters(lat, lon, p.lat, p.lon)
        if (m <= radiusMeters && m < bestM) {
          best = area
          bestM = m
        }
      }
    }
    return best
  }

  companion object {
    const val RADIUS_METERS = 300.0

    fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
      val p1 = Math.toRadians(lat1)
      val p2 = Math.toRadians(lat2)
      val dp = p2 - p1
      val dl = Math.toRadians(lon2 - lon1)
      val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
      return 2 * 6_371_000.0 * asin(sqrt(h))
    }
  }
}
