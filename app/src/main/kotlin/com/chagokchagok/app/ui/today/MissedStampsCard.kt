package com.chagokchagok.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chagokchagok.app.navi.MissedStamp
import com.chagokchagok.app.region.RegionIndex
import com.chagokchagok.app.ui.components.GhostButton
import com.chagokchagok.app.ui.components.StampButton
import com.chagokchagok.app.ui.theme.Eyebrow
import com.chagokchagok.app.ui.theme.Palette
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * "빠진 도장 n곳": unstamped regions found in the navigation history. Each is stamped only when the user taps
 * "찍기" (on today, or an earlier day picked with the arrows) or dropped with "빼기".
 */
@Composable
fun MissedStampsCard(
  missed: List<MissedStamp>,
  regions: RegionIndex?,
  today: LocalDate,
  onStamp: (MissedStamp, LocalDate) -> Unit,
  onDismiss: (MissedStamp) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (missed.isEmpty()) return
  Column(modifier.fillMaxWidth().background(Palette.Card, RoundedCornerShape(20.dp)).padding(24.dp)) {
    Text("FROM NAVI", style = Eyebrow, color = Palette.Primary)
    Spacer(Modifier.height(4.dp))
    Text("빠진 도장 ${missed.size}곳", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
    Spacer(Modifier.height(4.dp))
    Text(
      "내비 기록에 있지만 아직 도장이 없는 곳이에요. 다녀온 날을 골라 찍어 주세요.",
      style = MaterialTheme.typography.bodyMedium,
      color = Palette.TextSecondary,
    )
    missed.forEach { item ->
      Spacer(Modifier.height(16.dp))
      MissedRow(item, regions, today, onStamp, onDismiss)
    }
  }
}

private val DayFormat = DateTimeFormatter.ofPattern("MM.dd")

@Composable
private fun MissedRow(
  item: MissedStamp,
  regions: RegionIndex?,
  today: LocalDate,
  onStamp: (MissedStamp, LocalDate) -> Unit,
  onDismiss: (MissedStamp) -> Unit,
) {
  val region = regions?.get(item.regionCode)
  // Days before today; only the date is ever stored.
  var daysAgo by rememberSaveable(item.regionCode) { mutableStateOf(0) }
  val date = today.minusDays(daysAgo.toLong())
  Column(Modifier.fillMaxWidth().background(Palette.Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
    Text(region?.name ?: item.regionCode, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
    Text(region?.sido.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      GhostButton("하루 전", { daysAgo += 1 }, Modifier.weight(1f))
      Text(
        date.format(DayFormat) + if (daysAgo == 0) "\n오늘" else "\n${daysAgo}일 전",
        style = MaterialTheme.typography.labelLarge,
        color = Palette.Text,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(84.dp),
      )
      GhostButton("하루 후", { if (daysAgo > 0) daysAgo -= 1 }, Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      GhostButton("빼기", { onDismiss(item) }, Modifier.weight(1f))
      StampButton("찍기", { onStamp(item, date) }, Modifier.weight(1.4f))
    }
  }
}
