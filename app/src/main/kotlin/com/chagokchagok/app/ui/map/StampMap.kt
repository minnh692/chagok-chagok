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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.atan2
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
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
 * @param selected region drawn with a dark outline, or null.
 * @param connectPins false draws the pins without the dotted line between them.
 * @param arrival plays the arrival animation once (car along the dotted line, region fill, STAMPED!); the
 *   caller passes null while driving restrictions are active. [onArrivalDone] is called when it ends.
 * @param onTap when set, the map can be pinched, double-tapped and dragged, and a tap reports the
 *   (longitude, latitude) under the finger. Null keeps the map static and ignores touches.
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
  selected: String? = null,
  onTap: ((lon: Double, lat: Double) -> Unit)? = null,
  connectPins: Boolean = true,
  arrival: Arrival? = null,
  onArrivalDone: () -> Unit = {},
) {
  BoxWithConstraints(modifier.clipToBounds()) {
    val density = LocalDensity.current
    val width = constraints.maxWidth.toFloat()
    val height = constraints.maxHeight.toFloat()
    val base = with(density) {
      geometry?.let { fit(it, width, height, 24.dp.toPx(), topInset.toPx(), bottomInset.toPx()) }
    }
    // User zoom and pan on top of the fitted map; 1x / zero when the map is static.
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val fit = base?.let { MapFit(it.left * zoom + pan.x, it.top * zoom + pan.y, it.scale * zoom) }
    // 0..1 over the arrival; 1 when there is none. Read only while drawing, so it redraws without recomposing.
    val arrivalProgress = remember(arrival) { Animatable(if (arrival == null) 1f else 0f) }
    LaunchedEffect(arrival) {
      if (arrival == null) return@LaunchedEffect
      arrivalProgress.animateTo(1f, tween(ARRIVAL_MS, easing = LinearEasing))
      onArrivalDone()
    }
    // The arriving region's pin and label appear once the stamp lands.
    val arriving = arrival != null && arrivalProgress.value < 1f
    val shownPins = if (arriving) pins.filter { it.code != arrival?.to } else pins
    // Pins in screen pixels, in visiting order.
    val points = remember(geometry, fit, shownPins) {
      if (geometry == null || fit == null) emptyList() else shownPins.mapNotNull { pin -> geometry.labels[pin.code]?.let(fit::toScreen) }
    }
    val textMeasurer = rememberTextMeasurer()
    val gestures = if (onTap == null || geometry == null || base == null) {
      Modifier
    } else {
      // Keeps the zoomed map covering the view: pan stays within what the extra zoom added.
      fun clamp(z: Float, p: Offset) = Offset(p.x.coerceIn(width * (1 - z), 0f), p.y.coerceIn(height * (1 - z), 0f))
      fun zoomAround(center: Offset, newZoom: Float, extraPan: Offset = Offset.Zero) {
        val z = newZoom.coerceIn(1f, MAX_ZOOM)
        pan = clamp(z, center - (center - pan) * (z / zoom) + extraPan)
        zoom = z
      }
      Modifier
        .pointerInput(geometry, base) {
          detectTransformGestures { centroid, dragged, factor, _ -> zoomAround(centroid, zoom * factor, dragged) }
        }
        .pointerInput(geometry, base, onTap) {
          detectTapGestures(
            onDoubleTap = { at -> zoomAround(at, if (zoom < MAX_ZOOM / 2) zoom * DOUBLE_TAP_ZOOM else 1f) },
            onTap = { at ->
              val f = MapFit(base.left * zoom + pan.x, base.top * zoom + pan.y, base.scale * zoom)
              val (lon, lat) = geometry.unproject((at.x - f.left) / f.scale, (at.y - f.top) / f.scale)
              onTap(lon, lat)
            },
          )
        }
    }

    Canvas(Modifier.fillMaxSize().then(gestures)) {
      drawRect(Palette.Sea)
      val geo = geometry ?: return@Canvas
      val f = fit ?: return@Canvas
      withTransform({
        translate(f.left, f.top)
        scale(f.scale, f.scale, pivot = Offset.Zero)
      }) {
        val t = arrivalProgress.value
        for ((code, path) in geo.regionPaths) {
          val fill = fills[code]
          if (fill != null && arrival != null && t < 1f && code == arrival.to) {
            drawPath(path, Palette.Land)
            drawPath(path, fill, alpha = arrivalFill(t, arrival.from != null))
          } else {
            drawPath(path, fill ?: Palette.Land)
          }
        }
        drawPath(geo.borders, Palette.RegionBorder, style = Stroke(width = 1.dp.toPx() / f.scale))
        selected?.let(geo::path)?.let { path ->
          drawPath(path, Palette.Selected, style = Stroke(width = 4.dp.toPx() / f.scale, join = StrokeJoin.Round))
        }
      }
      if (veil == null) drawRoute(points, connectPins)
      if (veil == null && arrival != null && arrivalProgress.value < 1f) {
        val to = geo.labels[arrival.to]?.let(f::toScreen)
        val from = arrival.from?.let { geo.labels[it] }?.let(f::toScreen)
        if (to != null) drawArrival(from, to, arrivalProgress.value, textMeasurer)
      }
      if (veil != null) drawRect(veil)
    }

    if (veil == null && points.isNotEmpty()) {
      PinLabels(shownPins, points, Modifier.fillMaxSize())
    }
  }
}

/** The arrival animation for a first stamp in [to], from the previous stamp's region [from] (if any). */
data class Arrival(val from: String?, val to: String)

/** Whole arrival length; the contest rule allows at most 3 seconds. */
private const val ARRIVAL_MS = 2_600

// Arrival phases as fractions of ARRIVAL_MS: the car drives, then the region fills, then the stamp lands.
private const val DRIVE_END = 0.55f
private const val FILL_END = 0.72f

private fun phase(t: Float, start: Float, end: Float) = ((t - start) / (end - start)).coerceIn(0f, 1f)

/** Fill alpha of the arriving region; without a previous stamp there is no drive and it fills at once. */
private fun arrivalFill(t: Float, drives: Boolean) = if (drives) phase(t, DRIVE_END, FILL_END) else phase(t, 0f, 0.25f)

/** Point on the same curve [drawRoute] uses between a and b, at s in 0..1. */
private fun curvePoint(a: Offset, b: Offset, s: Float): Offset {
  val c = Offset((a.x + b.x) / 2 - (b.y - a.y) * 0.2f, (a.y + b.y) / 2 + (b.x - a.x) * 0.2f)
  val u = 1 - s
  return a * (u * u) + c * (2 * u * s) + b * (s * s)
}

private fun DrawScope.drawArrival(from: Offset?, to: Offset, t: Float, textMeasurer: TextMeasurer) {
  if (from != null && hypot(to.x - from.x, to.y - from.y) >= 1f) {
    val s = FastOutSlowInEasing.transform(phase(t, 0f, DRIVE_END))
    // The dotted line grows behind the car.
    val steps = 48
    val path = Path().apply {
      moveTo(from.x, from.y)
      for (i in 1..steps) curvePoint(from, to, s * i / steps).let { lineTo(it.x, it.y) }
    }
    val dots = PathEffect.dashPathEffect(floatArrayOf(0.01f, 15.dp.toPx()))
    drawPath(path, Palette.MapShade.copy(alpha = 0.35f), style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots))
    drawPath(path, Palette.OrderLine, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots))
    if (t < FILL_END) {
      val p = curvePoint(from, to, s)
      val ahead = curvePoint(from, to, (s + 0.02f).coerceAtMost(1f))
      val back = curvePoint(from, to, (s - 0.02f).coerceAtLeast(0f))
      val angle = Math.toDegrees(atan2((ahead.y - back.y).toDouble(), (ahead.x - back.x).toDouble())).toFloat()
      drawCar(p, angle)
    }
  }
  val stamp = phase(t, FILL_END, 1f)
  if (stamp > 0f) {
    val layout = textMeasurer.measure(
      "STAMPED!",
      TextStyle(color = Palette.StampedToday, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp),
    )
    val pad = 12.dp.toPx()
    val w = layout.size.width + pad * 2
    val h = layout.size.height + pad
    // Lands from a bigger size, slightly tilted like a rubber stamp; kept inside the canvas.
    val scale = 1f + 0.6f * (1 - FastOutSlowInEasing.transform(stamp))
    val center = Offset(
      to.x.coerceIn(w / 2 + pad, size.width - w / 2 - pad),
      (to.y - h).coerceIn(h / 2 + pad, size.height - h / 2 - pad),
    )
    withTransform({
      rotate(-8f, center)
      scale(scale, scale, center)
    }) {
      val topLeft = Offset(center.x - w / 2, center.y - h / 2)
      drawRoundRect(Palette.MapLabel.copy(alpha = stamp), topLeft, Size(w, h), CornerRadius(10.dp.toPx()))
      drawRoundRect(
        Palette.StampedToday.copy(alpha = stamp), topLeft, Size(w, h), CornerRadius(10.dp.toPx()),
        style = Stroke(width = 3.dp.toPx()),
      )
      drawText(layout, topLeft = Offset(center.x - layout.size.width / 2, center.y - layout.size.height / 2), alpha = stamp)
    }
  }
}

/** A small top-down car pointing along [degrees] (0 = east). */
private fun DrawScope.drawCar(at: Offset, degrees: Float) {
  val len = 30.dp.toPx()
  val wid = 17.dp.toPx()
  rotate(degrees, at) {
    drawCircle(Palette.MapShade.copy(alpha = 0.35f), radius = len * 0.62f, center = at)
    val body = Offset(at.x - len / 2, at.y - wid / 2)
    drawRoundRect(Palette.StampedToday, body, Size(len, wid), CornerRadius(5.dp.toPx()))
    drawRoundRect(Palette.OrderLine, body, Size(len, wid), CornerRadius(5.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
    // Windshield toward the front, rear window toward the back.
    drawRoundRect(Palette.OrderLine.copy(alpha = 0.9f), Offset(at.x + len * 0.08f, at.y - wid * 0.32f), Size(len * 0.2f, wid * 0.64f), CornerRadius(2.dp.toPx()))
    drawRoundRect(Palette.OrderLine.copy(alpha = 0.6f), Offset(at.x - len * 0.36f, at.y - wid * 0.28f), Size(len * 0.14f, wid * 0.56f), CornerRadius(2.dp.toPx()))
  }
}

private const val MAX_ZOOM = 12f
private const val DOUBLE_TAP_ZOOM = 2.5f

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
private fun DrawScope.drawRoute(points: List<Offset>, connect: Boolean) {
  if (connect && points.size >= 2) {
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
      // A pin scrolled out of a zoomed map takes its label with it.
      if (p.x < 0 || p.y < 0 || p.x > maxW || p.y > maxH) return@mapIndexed null
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
