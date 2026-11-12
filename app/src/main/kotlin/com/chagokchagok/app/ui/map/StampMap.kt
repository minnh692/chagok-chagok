package com.chagokchagok.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chagokchagok.app.ui.theme.Palette
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** A stamp shown on the map with a label card: region name, a short tag and the sido. */
data class MapPin(val code: String, val title: String, val tag: String, val sub: String)

/**
 * The map of Korea's 230 si/gun/gu, drawn from the bundled boundaries (no map SDK).
 *
 * @param fills color per region code; regions not in the map use the land color.
 * @param pins today's stamps in order. With 2+ a curved white dotted line joins them; each gets a label card.
 * @param topInset / bottomInset space kept free for the header and the tabs.
 * @param veil drawn over everything (used while driving), or null.
 */
@Composable
fun StampMap(
  geometry: MapGeometry?,
  fills: Map<String, Color>,
  pins: List<MapPin>,
  modifier: Modifier = Modifier,
  topInset: Dp = 0.dp,
  bottomInset: Dp = 0.dp,
  veil: Color? = null,
) {
  BoxWithConstraints(modifier) {
    val density = LocalDensity.current
    val fit = with(density) {
      geometry?.let { fit(it, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), 24.dp.toPx(), topInset.toPx(), bottomInset.toPx()) }
    }
    // Pins in screen pixels, in visiting order.
    val points = remember(geometry, fit, pins) {
      if (geometry == null || fit == null) emptyList() else pins.mapNotNull { pin -> geometry.labels[pin.code]?.let(fit::toScreen) }
    }

    Canvas(Modifier.fillMaxSize()) {
      drawRect(Palette.Sea)
      val geo = geometry ?: return@Canvas
      val f = fit ?: return@Canvas
      withTransform({
        translate(f.left, f.top)
        scale(f.scale, f.scale, pivot = Offset.Zero)
      }) {
        for ((code, path) in geo.regionPaths) drawPath(path, fills[code] ?: Palette.Land)
        drawPath(geo.borders, Palette.RegionBorder, style = Stroke(width = 1.dp.toPx() / f.scale))
      }
      if (veil == null) drawRoute(points)
      if (veil != null) drawRect(veil)
    }

    if (veil == null && points.isNotEmpty()) {
      PinLabels(pins, points, Modifier.fillMaxSize())
    }
  }
}

/** Where the map sits inside the canvas: map units -> screen pixels. */
private data class MapFit(val left: Float, val top: Float, val scale: Float) {
  fun toScreen(p: Offset) = Offset(left + p.x * scale, top + p.y * scale)
}

private fun fit(geo: MapGeometry, width: Float, height: Float, pad: Float, topInset: Float, bottomInset: Float): MapFit? {
  val usableH = height - topInset - bottomInset - 2 * pad
  val scale = min((width - 2 * pad) / geo.width, usableH / geo.height)
  if (scale <= 0f) return null
  return MapFit(
    left = (width - geo.width * scale) / 2,
    top = topInset + pad + (usableH - geo.height * scale) / 2,
    scale = scale,
  )
}

/**
 * Today's route in screen pixels so the dots do not scale with the map: gently curved dotted segments
 * (always bending to the same side) and ring pins, the latest one larger.
 */
private fun DrawScope.drawRoute(points: List<Offset>) {
  if (points.size >= 2) {
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
      val a = points[i - 1]
      val b = points[i]
      val len = hypot(b.x - a.x, b.y - a.y)
      if (len < 1f) continue
      // Control point pushed sideways from the middle by a fifth of the segment length.
      val bend = 0.2f
      val cx = (a.x + b.x) / 2 - (b.y - a.y) * bend
      val cy = (a.y + b.y) / 2 + (b.x - a.x) * bend
      path.quadraticTo(cx, cy, b.x, b.y)
    }
    // A zero-length dash with round caps gives a row of round dots.
    val dots = PathEffect.dashPathEffect(floatArrayOf(0.01f, 15.dp.toPx()))
    drawPath(path, Palette.MapShade.copy(alpha = 0.35f), style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots))
    drawPath(path, Palette.OrderLine, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots))
  }
  points.forEachIndexed { i, p ->
    val latest = i == points.lastIndex
    val outer = (if (latest) 11.dp else 8.dp).toPx()
    drawCircle(Palette.OrderLine, radius = outer, center = p)
    drawCircle(Palette.StampedToday, radius = outer - 3.5.dp.toPx(), center = p)
  }
}

/**
 * Label cards next to each pin. Each label tries the right of its pin, then the left, then above/below,
 * and takes the first spot that stays on the map and covers no other label or pin.
 */
@Composable
private fun PinLabels(pins: List<MapPin>, points: List<Offset>, modifier: Modifier) {
  val density = LocalDensity.current
  val gap = with(density) { 18.dp.roundToPx() }
  val margin = with(density) { 12.dp.roundToPx() }
  val pinBox = with(density) { 14.dp.roundToPx() }
  Layout(
    modifier = modifier,
    content = { pins.forEach { PinLabel(it) } },
  ) { measurables, constraints ->
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val placeables = measurables.map { it.measure(loose) }
    val maxW = constraints.maxWidth
    val maxH = constraints.maxHeight
    val taken = points.map { IntRect(it.x.roundToInt() - pinBox, it.y.roundToInt() - pinBox, it.x.roundToInt() + pinBox, it.y.roundToInt() + pinBox) }.toMutableList()
    val spots = placeables.mapIndexed { i, placeable ->
      val p = points.getOrNull(i) ?: return@mapIndexed null
      val px = p.x.roundToInt()
      val py = p.y.roundToInt()
      val w = placeable.width
      val h = placeable.height
      val candidates = listOf(
        IntOffset(px + gap, py - h / 2),
        IntOffset(px - gap - w, py - h / 2),
        IntOffset(px + gap, py + gap / 2),
        IntOffset(px - gap - w, py + gap / 2),
        IntOffset(px + gap, py - gap / 2 - h),
        IntOffset(px - gap - w, py - gap / 2 - h),
      ).map { IntOffset(it.x.coerceIn(margin, (maxW - margin - w).coerceAtLeast(margin)), it.y.coerceIn(margin, (maxH - margin - h).coerceAtLeast(margin))) }
      val ownPin = i
      val spot = candidates.firstOrNull { c ->
        val r = IntRect(c.x, c.y, c.x + w, c.y + h)
        taken.withIndex().none { (j, t) -> j != ownPin && t.overlaps(r) }
      } ?: candidates.first()
      taken += IntRect(spot.x, spot.y, spot.x + w, spot.y + h)
      spot
    }
    layout(maxW, maxH) {
      placeables.forEachIndexed { i, placeable -> spots[i]?.let { placeable.place(it) } }
    }
  }
}

@Composable
private fun PinLabel(pin: MapPin) {
  Box(
    Modifier
      .background(Palette.MapLabel, RoundedCornerShape(8.dp))
      .padding(horizontal = 12.dp, vertical = 6.dp),
  ) {
    Column {
      Text(pin.title, color = Palette.MapLabelText, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(pin.tag, color = Palette.StampedToday, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
      Text(pin.sub, color = Palette.MapLabelSub, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
  }
}
