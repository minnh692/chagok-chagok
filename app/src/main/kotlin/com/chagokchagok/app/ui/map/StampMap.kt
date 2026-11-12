package com.chagokchagok.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chagokchagok.app.ui.theme.Palette
import kotlin.math.min

/**
 * The map of Korea's 230 si/gun/gu, drawn from the bundled boundaries (no map SDK).
 *
 * @param fills color per region code; regions not in the map use the land color.
 * @param topInset space kept free above the map for the header.
 * @param orderCodes today's stamps in order; with 2+ a white dotted line joins their label points.
 */
@Composable
fun StampMap(
  geometry: MapGeometry?,
  fills: Map<String, Color>,
  orderCodes: List<String>,
  modifier: Modifier = Modifier,
  topInset: Dp = 0.dp,
) {
  Canvas(modifier) {
    drawRect(Palette.Sea)
    val geo = geometry ?: return@Canvas
    val pad = 24.dp.toPx()
    val inset = topInset.toPx()
    val scale = min((size.width - 2 * pad) / geo.width, (size.height - inset - 2 * pad) / geo.height)
    if (scale <= 0f) return@Canvas
    val left = (size.width - geo.width * scale) / 2
    val top = inset + (size.height - inset - geo.height * scale) / 2

    withTransform({
      translate(left, top)
      scale(scale, scale, pivot = Offset.Zero)
    }) {
      for ((code, path) in geo.regionPaths) drawPath(path, fills[code] ?: Palette.Land)
      drawPath(geo.borders, Palette.RegionBorder, style = Stroke(width = 1.dp.toPx() / scale))
    }

    // Order line and dots in screen pixels so the dash pattern does not scale with the map.
    val points = orderCodes.mapNotNull { geo.labels[it] }.map { Offset(left + it.x * scale, top + it.y * scale) }
    if (points.size < 2) return@Canvas
    run {
      val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
      for (i in 0 until points.size - 1) {
        drawLine(
          Palette.OrderLine, points[i], points[i + 1],
          strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash,
        )
      }
    }
    for (p in points) {
      drawCircle(Palette.OrderLine, radius = 5.dp.toPx(), center = p)
    }
  }
}
