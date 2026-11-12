package com.chagokchagok.app.ui.next

import com.chagokchagok.app.ui.components.enterOnce
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chagokchagok.app.collection.CollectionProgress
import com.chagokchagok.app.collection.NextStamp
import com.chagokchagok.app.data.StampBook
import com.chagokchagok.app.ui.Handoff
import com.chagokchagok.app.ui.Recommendations
import com.chagokchagok.app.ui.components.GhostButton
import com.chagokchagok.app.ui.components.StampButton
import com.chagokchagok.app.ui.map.MapFilter
import com.chagokchagok.app.ui.map.MapGeometry
import com.chagokchagok.app.ui.map.MapPin
import com.chagokchagok.app.ui.map.StampMap
import com.chagokchagok.app.ui.map.fillColors
import com.chagokchagok.app.ui.map.mapFills
import com.chagokchagok.app.ui.theme.Eyebrow
import com.chagokchagok.app.ui.theme.Palette
import kotlin.math.roundToInt

/**
 * "다음 도장 추천" (C2) and its hand-off confirm (C3). The list shows the 3 nearest unstamped regions;
 * "내비로 안내" sends the region to the navigation right away and shows what a stamp there would collect.
 */
@Composable
fun NextStampScreen(
  geometry: MapGeometry?,
  book: StampBook,
  recommendations: Recommendations?,
  confirming: String?,
  handoff: Handoff,
  onConfirm: (String?) -> Unit,
  onStart: (String) -> Unit,
  onBack: () -> Unit,
) {
  val items = recommendations?.items.orEmpty()
  val chosen = items.firstOrNull { it.region.code == confirming }
  val fills = remember(book) { fillColors(mapFills(book, MapFilter.MINE)) }
  val pins = remember(recommendations, chosen) {
    val from = recommendations?.from?.let { MapPin(it.code, it.name, "지금 여기", it.sido) }
    val shown = if (chosen != null) listOf(chosen) else items
    listOfNotNull(from) + shown.mapIndexed { i, n ->
      MapPin(n.region.code, n.region.name, if (chosen != null) "다음 도장" else "${i + 1} · 새 도장 +1", n.region.sido)
    }
  }

  val map = @Composable { modifier: Modifier ->
    Box(modifier) {
      StampMap(geometry, fills, pins, Modifier.fillMaxSize(), topInset = 130.dp, bottomInset = 24.dp, connectPins = false)
      Box(
        Modifier.fillMaxWidth().height(180.dp).background(
          Brush.verticalGradient(0f to Palette.MapShade.copy(alpha = 0.97f), 1f to Palette.MapShade.copy(alpha = 0f)),
        ),
      )
      Column(Modifier.padding(28.dp)) {
        Text("NEXT STAMP", style = Eyebrow, color = Palette.Primary)
        Spacer(Modifier.height(4.dp))
        Text("다음 도장 추천", style = MaterialTheme.typography.displaySmall, color = Palette.Text)
      }
    }
  }
  val panel = @Composable { modifier: Modifier ->
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
      when {
        chosen != null -> Confirm(chosen, book.total, handoff, onCancel = { onConfirm(null) }, onStart = { onStart(chosen.region.code) }, onDone = onBack)
        recommendations == null -> Note("추천을 준비하고 있어요", "")
        recommendations.from == null -> Note("첫 도장을 찍으면 추천해 드려요", "주차하고 차곡차곡을 열면 그곳에 첫 도장을 찍어요.")
        items.isEmpty() -> Note("가까운 곳은 모두 모았어요", "전국 시·군·구에 도장을 다 찍었어요.")
        else -> {
          Text(
            "${recommendations.from.name}에서 가까운, 아직 도장이 없는 곳",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
            color = Palette.TextSecondary,
          )
          Spacer(Modifier.height(16.dp))
          items.forEachIndexed { i, n ->
            if (i > 0) Spacer(Modifier.height(14.dp))
            Candidate(i + 1, n, onGuide = { onStart(n.region.code) })
          }
        }
      }
      if (chosen == null) {
        Spacer(Modifier.height(20.dp))
        GhostButton("돌아가기", onBack, Modifier.fillMaxWidth())
      }
    }
  }

  BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Panel)) {
    if (maxWidth > maxHeight) {
      val panelWidth = maxWidth * 0.4f
      Row(Modifier.fillMaxSize()) {
        map(Modifier.weight(1f).fillMaxHeight())
        panel(Modifier.width(panelWidth).fillMaxHeight())
      }
    } else {
      Column(Modifier.fillMaxSize()) {
        map(Modifier.fillMaxWidth().weight(0.45f))
        panel(Modifier.fillMaxWidth().weight(0.55f).background(Palette.Panel, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)))
      }
    }
  }
}

@Composable
private fun Note(title: String, body: String) {
  Column(Modifier.fillMaxWidth().background(Palette.Card, RoundedCornerShape(20.dp)).padding(24.dp)) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
    if (body.isNotEmpty()) {
      Spacer(Modifier.height(6.dp))
      Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
    }
  }
}

@Composable
private fun Candidate(number: Int, n: NextStamp, onGuide: () -> Unit) {
  Column(Modifier.enterOnce(n.region.code, delayMs = (number - 1) * 90).fillMaxWidth().background(Palette.Card, RoundedCornerShape(20.dp)).padding(20.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(36.dp).background(Palette.Primary, CircleShape), contentAlignment = Alignment.Center) {
        Text("$number", style = MaterialTheme.typography.titleMedium, color = Palette.OnPrimary)
      }
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(n.region.name, style = MaterialTheme.typography.titleLarge, color = Palette.Text, maxLines = 1)
        Text(
          "${n.region.sido} · ${if (n.adjacent) "맞닿은 곳 · " else ""}약 ${n.km.roundToInt()}km",
          style = MaterialTheme.typography.bodyMedium,
          color = Palette.TextSecondary,
          maxLines = 1,
        )
      }
    }
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Tag("새 도장 +1", strong = true)
      n.advances.forEach { Tag(advanceLabel(it), strong = false) }
    }
    Spacer(Modifier.height(14.dp))
    StampButton("내비로 안내", onGuide, Modifier.fillMaxWidth())
  }
}

private fun advanceLabel(p: CollectionProgress) =
  if (p.kind == CollectionProgress.Kind.COAST) "바다 ${p.done + 1}/${p.target}" else "${p.title} ${p.done + 1}/${p.target}"

@Composable
private fun Tag(text: String, strong: Boolean) {
  Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = if (strong) Palette.OnPrimary else Palette.Text,
    modifier = Modifier
      .background(if (strong) Palette.Primary else Palette.Panel, RoundedCornerShape(10.dp))
      .border(1.dp, if (strong) Palette.Primary else Palette.Outline, RoundedCornerShape(10.dp))
      .padding(horizontal = 12.dp, vertical = 6.dp),
  )
}

/** C3: what a stamp there collects and how the hand-off went, with "다시 보내기". */
@Composable
private fun Confirm(n: NextStamp, total: Int, handoff: Handoff, onCancel: () -> Unit, onStart: () -> Unit, onDone: () -> Unit) {
  Column(Modifier.enterOnce(n.region.code).fillMaxWidth().animateContentSize().background(Palette.Card, RoundedCornerShape(20.dp)).padding(24.dp)) {
    Text("NAVI · 목적지 확인", style = Eyebrow, color = Palette.Primary)
    Spacer(Modifier.height(6.dp))
    Text(n.region.name, style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
    Text(n.region.sido, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal), color = Palette.TextSecondary)
    Spacer(Modifier.height(18.dp))
    Text("이번에 모을 수 있는 것", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
    Spacer(Modifier.height(10.dp))
    Gain("새 도장 +1", "$total → ${total + 1} STAMPS")
    n.advances.forEach { p ->
      Spacer(Modifier.height(8.dp))
      Gain(if (p.kind == CollectionProgress.Kind.COAST) "바다를 낀 시·군·구" else p.title, "${p.done}/${p.target} → ${p.done + 1}/${p.target}")
    }
    Spacer(Modifier.height(20.dp))
    when (handoff) {
      Handoff.Idle -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GhostButton("취소", onCancel, Modifier.weight(1f))
        StampButton("내비로 보내기", onStart, Modifier.weight(1.4f))
      }
      Handoff.Requesting -> GhostButton("내비에 목적지를 보내는 중…", {}, Modifier.fillMaxWidth())
      Handoff.Started -> {
        Result("내비 안내를 시작했어요", "${n.region.name}에 도착해서 주차하면 도장을 찍어요.")
        Spacer(Modifier.height(14.dp))
        StampButton("확인", onDone, Modifier.fillMaxWidth())
      }
      Handoff.Sent -> {
        Result("내비에 ${n.region.name}${objectParticle(n.region.name)} 보냈어요", "내비 화면에서 경로를 확인하세요. 도착해서 주차하면 도장을 찍어요.")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          GhostButton("다시 보내기", onStart, Modifier.weight(1f))
          StampButton("확인", onDone, Modifier.weight(1.4f))
        }
      }
      Handoff.Manual -> {
        Result("내비에 연결하지 못했어요", "내비에서 직접 입력해 주세요 · 목적지: ${n.region.sido} ${n.region.name}")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          GhostButton("다시 보내기", onStart, Modifier.weight(1f))
          StampButton("확인", onDone, Modifier.weight(1.4f))
        }
      }
    }
  }
}

@Composable
private fun Gain(title: String, value: String) {
  Row(
    Modifier.fillMaxWidth().background(Palette.Panel, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.Text, modifier = Modifier.weight(1f))
    Text(value, style = MaterialTheme.typography.labelMedium, color = Palette.Primary)
  }
}

@Composable
private fun Result(title: String, body: String) {
  Column(Modifier.fillMaxWidth().border(1.5.dp, Palette.Primary, RoundedCornerShape(14.dp)).padding(16.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
    Spacer(Modifier.height(4.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
  }
}

/** "을" after a final consonant (받침), otherwise "를". */
internal fun objectParticle(word: String): String {
  val c = word.lastOrNull() ?: return "를"
  return if (c in '가'..'힣' && (c - '가') % 28 != 0) "을" else "를"
}
