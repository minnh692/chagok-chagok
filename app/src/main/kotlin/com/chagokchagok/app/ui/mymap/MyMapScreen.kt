package com.chagokchagok.app.ui.mymap

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chagokchagok.app.collection.CollectionProgress
import com.chagokchagok.app.collection.Collections
import com.chagokchagok.app.data.StampBook
import com.chagokchagok.app.region.RegionFinder
import com.chagokchagok.app.region.RegionIndex
import com.chagokchagok.app.ui.components.GhostButton
import com.chagokchagok.app.ui.components.StampButton
import com.chagokchagok.app.ui.components.MapLegend
import com.chagokchagok.app.ui.map.MapFilter
import com.chagokchagok.app.ui.map.MapGeometry
import com.chagokchagok.app.ui.map.MapPin
import com.chagokchagok.app.ui.map.MapTabs
import com.chagokchagok.app.ui.map.StampMap
import com.chagokchagok.app.ui.map.fillColors
import com.chagokchagok.app.ui.map.mapFills
import com.chagokchagok.app.ui.theme.Eyebrow
import com.chagokchagok.app.ui.theme.Palette
import com.chagokchagok.app.ui.today.ProgressBar
import java.time.LocalDate
import kotlin.math.roundToInt
import java.time.format.DateTimeFormatter

/**
 * "나의 지도" (C4): the whole stamp map to explore. Pinch / double-tap to zoom, drag to pan, tap a region
 * to see its stamps. Wide: map left, detail panel right. Narrow (split): the panel is a sheet under the map.
 */
@Composable
fun MyMapScreen(
  regions: RegionIndex?,
  geometry: MapGeometry?,
  book: StampBook,
  filter: MapFilter,
  collections: Collections?,
  onFilter: (MapFilter) -> Unit,
  onBack: () -> Unit,
  onOpenNext: () -> Unit,
) {
  var selectedCode by rememberSaveable { mutableStateOf<String?>(null) }
  val selected = selectedCode?.let { regions?.get(it) }
  val detail = remember(selected, book) { selected?.let { RegionDetail.of(it, book) } }
  val finder = remember(regions) { regions?.let(::RegionFinder) }
  val fills = remember(book, filter) { fillColors(mapFills(book, filter)) }
  val progress = remember(book, collections) { collections?.progress(book).orEmpty() }
  val pins = remember(detail) { listOfNotNull(detail?.let(::calloutPin)) }
  val regionCount = regions?.size ?: 230

  val map = @Composable { modifier: Modifier ->
    Box(modifier) {
      StampMap(
        geometry, fills, pins, Modifier.fillMaxSize(),
        topInset = 150.dp, bottomInset = 110.dp,
        selected = selectedCode,
        // Tapping the sea clears the selection.
        onTap = { lon, lat -> selectedCode = finder?.find(lat, lon)?.code },
      )
      Box(
        Modifier
          .fillMaxWidth()
          .height(200.dp)
          .background(
            Brush.verticalGradient(
              0f to Palette.MapShade.copy(alpha = 0.97f),
              0.55f to Palette.MapShade.copy(alpha = 0.8f),
              1f to Palette.MapShade.copy(alpha = 0f),
            ),
          ),
      )
      Header(fills.size, regionCount, Modifier.padding(28.dp))
      GhostButton("오늘로", onBack, Modifier.align(Alignment.TopEnd).padding(24.dp).width(140.dp))
      Column(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.End,
      ) {
        MapLegend()
        Spacer(Modifier.height(12.dp))
        MapTabs(
          selected = filter,
          onSelect = onFilter,
          modifier = Modifier.align(Alignment.CenterHorizontally).horizontalScroll(rememberScrollState()),
        )
      }
    }
  }
  val panel = @Composable { modifier: Modifier ->
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
      Text("선택한 지역", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
      Spacer(Modifier.height(12.dp))
      if (detail == null) SelectHint() else RegionDetailCard(detail)
      Spacer(Modifier.height(16.dp))
      StampButton("다음 도장 추천", onOpenNext, Modifier.fillMaxWidth())
      Spacer(Modifier.height(28.dp))
      CollectionList("시·도별 컬렉션", progress.filter { it.kind == CollectionProgress.Kind.SIDO })
      Spacer(Modifier.height(28.dp))
      CollectionList("다른 컬렉션", progress.filter { it.kind != CollectionProgress.Kind.SIDO })
    }
  }

  BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Panel)) {
    if (maxWidth > maxHeight) {
      val panelWidth = maxWidth * 0.36f
      Row(Modifier.fillMaxSize()) {
        map(Modifier.weight(1f).fillMaxHeight())
        panel(Modifier.width(panelWidth).fillMaxHeight())
      }
    } else {
      Column(Modifier.fillMaxSize()) {
        map(Modifier.fillMaxWidth().weight(0.55f))
        panel(
          Modifier
            .fillMaxWidth()
            .weight(0.45f)
            .background(Palette.Panel, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        )
      }
    }
  }
}

private val ShortDate = DateTimeFormatter.ofPattern("MM.dd")
private val LongDate = DateTimeFormatter.ofPattern("yyyy.MM.dd")

private fun formatDate(date: String, pattern: DateTimeFormatter) =
  runCatching { LocalDate.parse(date).format(pattern) }.getOrDefault(date)

/** The white callout next to the selected region. */
private fun calloutPin(d: RegionDetail) = MapPin(
  code = d.code,
  title = d.name,
  tag = d.firstDate?.let { "FIRST STAMP · ${formatDate(it, ShortDate)}" } ?: "아직 도장 없음",
  sub = if (d.stamped) "${d.sido} · 방문 ${d.visitCount}회" else d.sido,
)

@Composable
private fun Header(count: Int, regionCount: Int, modifier: Modifier) {
  val percent = if (regionCount == 0) 0 else (count * 100f / regionCount).roundToInt()
  Column(modifier) {
    Text("MY KOREA  ·  CHAGOK MAP", style = Eyebrow, color = Palette.Primary)
    Spacer(Modifier.height(4.dp))
    Text("$count STAMPS", style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 1.sp), color = Palette.Text)
    Spacer(Modifier.height(4.dp))
    Text(
      "전국 시·군·구 ${regionCount}곳 중 ${count}곳 · $percent%",
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
      color = Palette.TextSecondary,
    )
  }
}

@Composable
private fun SelectHint() {
  Column(
    Modifier.fillMaxWidth().background(Palette.Card, RoundedCornerShape(20.dp)).padding(24.dp),
  ) {
    Text("지도에서 시·군·구를 눌러 보세요", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
    Spacer(Modifier.height(6.dp))
    Text(
      "두 손가락으로 벌리거나 두 번 누르면 확대돼요. 서울처럼 작은 구도 확대해서 고를 수 있어요.",
      style = MaterialTheme.typography.bodyMedium,
      color = Palette.TextSecondary,
    )
  }
}

@Composable
private fun RegionDetailCard(d: RegionDetail) {
  Column(Modifier.fillMaxWidth().background(Palette.Card, RoundedCornerShape(20.dp)).padding(24.dp)) {
    Text(d.name, style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
    Text(d.sido, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal), color = Palette.TextSecondary)
    Spacer(Modifier.height(16.dp))
    if (!d.stamped) {
      Text("아직 도장이 없어요", style = MaterialTheme.typography.titleMedium, color = Palette.Primary)
      return@Column
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Stat("첫 도장", formatDate(d.firstDate!!, LongDate), Modifier.weight(1.4f))
      Stat("방문", "${d.visitCount}회", Modifier.weight(1f))
      Stat("동행", d.companions.joinToString(", ") { it.label }.ifEmpty { "—" }, Modifier.weight(1.2f))
    }
    if (d.memos.isNotEmpty()) {
      Spacer(Modifier.height(16.dp))
      Text("메모", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
      d.memos.forEach { memo ->
        Spacer(Modifier.height(4.dp))
        Text("“$memo”", style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
      }
    }
  }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
  Column(
    modifier.background(Palette.Panel, RoundedCornerShape(14.dp)).padding(vertical = 12.dp, horizontal = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal), color = Palette.TextSecondary, maxLines = 1)
    Text(value, style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 1)
  }
}

/** Every collection of one group with a bar, e.g. "경기도  3 / 31". Not interactive. */
@Composable
private fun CollectionList(title: String, items: List<CollectionProgress>) {
  if (items.isEmpty()) return
  Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
  Spacer(Modifier.height(12.dp))
  Column(
    Modifier.fillMaxWidth().background(Palette.Card, RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    items.forEach { p ->
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(p.title, style = MaterialTheme.typography.bodyLarge, color = Palette.Text, modifier = Modifier.weight(1f), maxLines = 1)
          Text(
            "${p.done} / ${p.target}",
            style = MaterialTheme.typography.labelMedium,
            color = if (p.complete) Palette.Primary else Palette.TextSecondary,
          )
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(p.fraction, Modifier.fillMaxWidth().height(6.dp))
      }
    }
  }
}
