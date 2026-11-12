package com.chagokchagok.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chagokchagok.app.ui.components.StatusPill
import com.chagokchagok.app.ui.map.MapGeometry
import com.chagokchagok.app.ui.map.StampMap
import com.chagokchagok.app.ui.theme.Palette
import androidx.compose.ui.graphics.Color

/**
 * Shown instead of the whole UI while driving restrictions are active: the stamp map under a dark veil,
 * a "driving" pill and one centered notice.
 * Static on purpose: no buttons, no animation, nothing to read twice. Every touch is consumed here and
 * nothing is focusable, so no input reaches the app while driving.
 */
@Composable
fun DrivingBlockScreen(geometry: MapGeometry?, fills: Map<String, Color>) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
      }
      .background(Palette.Panel),
  ) {
    StampMap(
      geometry = geometry,
      fills = fills,
      pins = emptyList(),
      modifier = Modifier.fillMaxSize(),
      veil = Palette.MapShade.copy(alpha = 0.72f),
    )
    StatusPill("주행 중", dot = Palette.Primary, modifier = Modifier.align(Alignment.TopEnd).padding(28.dp))

    Column(
      modifier = Modifier
        .align(Alignment.Center)
        .padding(32.dp)
        .widthIn(max = 640.dp)
        .background(Palette.Panel, RoundedCornerShape(24.dp))
        .padding(horizontal = 48.dp, vertical = 40.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .size(88.dp)
          .background(Palette.Card, CircleShape)
          .border(2.dp, Palette.Outline, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Text("D", color = Palette.Text, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
      }
      Text(
        "주행 중에는 사용할 수 없어요",
        style = MaterialTheme.typography.headlineMedium,
        color = Palette.Text,
        textAlign = TextAlign.Center,
      )
      Text(
        "주차(P)하면 오늘의 도장을 보여드릴게요",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
        color = Palette.TextSecondary,
        textAlign = TextAlign.Center,
      )
    }

    Text(
      "운전 중에는 조작할 것이 없어요 · 기록은 차를 세웠을 때만",
      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
      color = Palette.TextSecondary,
      textAlign = TextAlign.Center,
      modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
    )
  }
}
