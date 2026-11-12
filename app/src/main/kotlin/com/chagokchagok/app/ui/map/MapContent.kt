package com.chagokchagok.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import com.chagokchagok.app.data.StampBook
import com.chagokchagok.app.region.RegionIndex
import com.chagokchagok.app.ui.theme.Palette

/** The map Paths, built once per process off the main thread; null until ready. */
@Composable
fun rememberMapGeometry(regions: RegionIndex?): MapGeometry? {
  val geometry by produceState<MapGeometry?>(null, regions) {
    value = regions?.let { MapGeometry.of(it) }
  }
  return geometry
}

/** Fill colors for [mapFills] output. */
fun fillColors(fills: Map<String, Fill>): Map<String, Color> =
  fills.mapValues { (_, fill) -> if (fill == Fill.TODAY) Palette.StampedToday else Palette.StampedBefore }

/** Today's stamps in visiting order, as labelled map pins. */
fun todayPins(book: StampBook, regions: RegionIndex?): List<MapPin> =
  book.todayStamps.map { stamp ->
    val region = regions?.get(stamp.regionCode)
    MapPin(
      code = stamp.regionCode,
      title = region?.name ?: stamp.regionCode,
      tag = if (book.isNewToday(stamp.regionCode)) "NEW STAMP" else "${book.visitCount(stamp.regionCode)}번째 방문",
      sub = region?.sido.orEmpty(),
    )
  }
