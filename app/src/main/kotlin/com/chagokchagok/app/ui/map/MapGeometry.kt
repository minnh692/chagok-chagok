package com.chagokchagok.app.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import com.chagokchagok.app.region.Bounds
import com.chagokchagok.app.region.RegionIndex
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Region outlines turned into drawing Paths once, in "map units" (an equirectangular projection scaled by
 * cos(center latitude)). The canvas only scales and translates them, so nothing is rebuilt per frame.
 */
class MapGeometry private constructor(index: RegionIndex) {
  private val bounds: Bounds = Bounds.union(index.regions.map { it.bounds })
  private val lonScale = cos(Math.toRadians((bounds.minLat + bounds.maxLat) / 2)).toFloat() * UNITS_PER_DEGREE

  val width: Float = ((bounds.maxLon - bounds.minLon) * lonScale).toFloat()
  val height: Float = ((bounds.maxLat - bounds.minLat) * UNITS_PER_DEGREE).toFloat()

  /** One filled Path per region code. */
  val regionPaths: List<Pair<String, Path>>

  private val pathByCode: Map<String, Path> by lazy { regionPaths.toMap() }

  /** The filled Path of one region, or null for an unknown code. */
  fun path(code: String): Path? = pathByCode[code]

  /** Every outline in one Path, stroked in a single draw call. */
  val borders = Path()

  /** Label point (always inside the region) per region code, in map units. */
  val labels: Map<String, Offset>

  init {
    regionPaths = index.regions.map { region ->
      val path = Path().apply { fillType = PathFillType.EvenOdd }
      for (polygon in region.polygons) {
        for (ring in polygon.rings) addRing(path, ring)
      }
      borders.addPath(path)
      region.code to path
    }
    labels = index.regions.associate { it.code to project(it.labelLon, it.labelLat) }
  }

  fun project(lon: Double, lat: Double) = Offset(
    ((lon - bounds.minLon) * lonScale).toFloat(),
    ((bounds.maxLat - lat) * UNITS_PER_DEGREE).toFloat(),
  )

  /** Inverse of [project]: map units to (lon, lat). */
  fun unproject(x: Float, y: Float): Pair<Double, Double> =
    (x / lonScale + bounds.minLon) to (bounds.maxLat - y / UNITS_PER_DEGREE)

  /** Adds a ring, dropping points closer than [SIMPLIFY_DEGREES] to the last kept one (well under a pixel). */
  private fun addRing(path: Path, ring: DoubleArray) {
    val n = ring.size / 2
    if (n < 3) return
    val keep = BooleanArray(n)
    var kept = 1
    keep[0] = true
    var lastLon = ring[0]
    var lastLat = ring[1]
    for (i in 1 until n) {
      val lon = ring[2 * i]
      val lat = ring[2 * i + 1]
      if (max(abs(lon - lastLon), abs(lat - lastLat)) >= SIMPLIFY_DEGREES) {
        keep[i] = true
        kept++
        lastLon = lon
        lastLat = lat
      }
    }
    // Tiny islands would collapse; keep them as they are so every region stays visible.
    val all = kept < 4
    var first = true
    for (i in 0 until n) {
      if (!all && !keep[i]) continue
      val p = project(ring[2 * i], ring[2 * i + 1])
      if (first) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
      first = false
    }
    path.close()
  }

  companion object {
    private const val UNITS_PER_DEGREE = 100f
    private const val SIMPLIFY_DEGREES = 0.003

    private val mutex = Mutex()
    @Volatile private var cached: Pair<RegionIndex, MapGeometry>? = null

    /** Builds the Paths once per process, off the main thread. */
    suspend fun of(index: RegionIndex): MapGeometry {
      cached?.takeIf { it.first === index }?.let { return it.second }
      return mutex.withLock {
        cached?.takeIf { it.first === index }?.second
          ?: withContext(Dispatchers.Default) { MapGeometry(index) }.also { cached = index to it }
      }
    }
  }
}
