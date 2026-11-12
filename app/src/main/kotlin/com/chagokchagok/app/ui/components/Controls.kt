package com.chagokchagok.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chagokchagok.app.ui.theme.MinTouch
import com.chagokchagok.app.ui.theme.Palette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Corner radius shared by buttons and small cards. */
val ButtonShape = RoundedCornerShape(14.dp)

private val ButtonPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)

/** Main action ("stamp" red). */
@Composable
fun StampButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Button(
    onClick = onClick,
    modifier = modifier.heightIn(min = MinTouch),
    shape = ButtonShape,
    contentPadding = ButtonPadding,
    colors = ButtonDefaults.buttonColors(containerColor = Palette.Primary, contentColor = Palette.OnPrimary),
  ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

/** Secondary action: card fill with a thin outline. */
@Composable
fun GhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Button(
    onClick = onClick,
    modifier = modifier.heightIn(min = MinTouch),
    shape = ButtonShape,
    contentPadding = ButtonPadding,
    border = BorderStroke(1.5.dp, Palette.Outline),
    colors = ButtonDefaults.buttonColors(containerColor = Palette.Card, contentColor = Palette.Text),
  ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

/** Car state pill shown in the top-right corner of the map ("● 주차 중"). Not interactive. */
@Composable
fun StatusPill(text: String, dot: Color, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .background(Palette.Panel.copy(alpha = 0.9f), CircleShape)
      .padding(horizontal = 18.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(10.dp).background(dot, CircleShape))
    Text(text, style = MaterialTheme.typography.labelMedium, color = Palette.Text)
  }
}

/** Map legend: today vs earlier stamps. */
@Composable
fun MapLegend(modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .background(Palette.Panel.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
      .padding(horizontal = 14.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    LegendItem(Palette.StampedToday, "오늘")
    LegendItem(Palette.StampedBefore, "이전 도장")
  }
}

@Composable
private fun LegendItem(color: Color, label: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(14.dp).background(color, RoundedCornerShape(3.dp)))
    Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Text)
  }
}

private val EyebrowDate = DateTimeFormatter.ofPattern("MM.dd EEE", Locale.ENGLISH)

/** "11.12 THU" for eyebrow lines. */
fun eyebrowDate(date: LocalDate): String = date.format(EyebrowDate).uppercase(Locale.ENGLISH)
