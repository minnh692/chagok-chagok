package com.chagokchagok.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chagokchagok.app.data.CompanionType
import com.chagokchagok.app.data.StampBook
import com.chagokchagok.app.region.RegionIndex
import com.chagokchagok.app.ui.theme.MinTouch
import com.chagokchagok.app.ui.theme.Palette

/** Today's stamps in order: new regions get a NEW badge, revisits show the visit count. */
@Composable
fun TodayStampList(book: StampBook, regions: RegionIndex?, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.Bottom) {
      Text("오늘의 도장", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
      Spacer(Modifier.width(12.dp))
      Text(
        "${book.todayNewStamps.size} NEW STAMPS",
        style = MaterialTheme.typography.labelMedium,
        color = Palette.StampedToday,
      )
    }
    Spacer(Modifier.height(12.dp))
    if (book.todayStamps.isEmpty()) {
      Text("아직 오늘 찍은 도장이 없어요.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
      return@Column
    }
    // A vertical timeline: numbered stops joined by a red line, in the order they were stamped.
    Column(
      Modifier
        .fillMaxWidth()
        .background(Palette.Card, RoundedCornerShape(20.dp))
        .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
      val last = book.todayStamps.lastIndex
      book.todayStamps.forEachIndexed { i, stamp ->
        val region = regions?.get(stamp.regionCode)
        Row(
          modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier
              .width(40.dp)
              .fillMaxHeight()
              .drawBehind {
                val x = size.width / 2
                val w = 3.dp.toPx()
                if (i > 0) drawLine(Palette.Primary, Offset(x, 0f), Offset(x, size.height / 2), w)
                if (i < last) drawLine(Palette.Primary, Offset(x, size.height / 2), Offset(x, size.height), w)
              },
            contentAlignment = Alignment.Center,
          ) {
            Box(Modifier.size(40.dp).background(Palette.StampedToday, CircleShape), contentAlignment = Alignment.Center) {
              Text("${stamp.dayOrder}", style = MaterialTheme.typography.labelLarge, color = Palette.OnPrimary)
            }
          }
          Spacer(Modifier.width(16.dp))
          Column(Modifier.weight(1f).padding(vertical = 14.dp)) {
            Text(region?.name ?: stamp.regionCode, style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(region?.sido.orEmpty(), style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary, maxLines = 1)
          }
          if (book.isNewToday(stamp.regionCode)) {
            Text("NEW", style = MaterialTheme.typography.labelLarge, color = Palette.StampedToday)
          } else {
            Text("${book.visitCount(stamp.regionCode)}번째 방문", style = MaterialTheme.typography.labelMedium, color = Palette.StampedBefore)
          }
        }
      }
    }
  }
}

/** "오늘 누구랑?": one tap saves the companion on today's stamps; tapping the chosen chip clears it. */
@Composable
fun CompanionChips(
  selected: CompanionType?,
  enabled: Boolean,
  onSelect: (CompanionType?) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier.fillMaxWidth()) {
    Text("오늘 누구랑?", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      CompanionType.entries.forEach { type ->
        val isSelected = type == selected
        Box(
          modifier = Modifier
            .weight(1f)
            .heightIn(min = MinTouch)
            .background(if (isSelected) Palette.Primary else Palette.Card, CircleShape)
            .border(1.5.dp, if (isSelected) Palette.Primary else Palette.Outline, CircleShape)
            .semantics { this.selected = isSelected }
            .clickable(enabled = enabled, role = Role.Button) { onSelect(if (isSelected) null else type) },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            type.label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
              isSelected -> Palette.OnPrimary
              enabled -> Palette.Text
              else -> Palette.TextSecondary.copy(alpha = 0.5f)
            },
          )
        }
      }
    }
    if (!enabled) {
      Spacer(Modifier.height(8.dp))
      Text("오늘 도장을 찍으면 고를 수 있어요.", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
    }
  }
}
