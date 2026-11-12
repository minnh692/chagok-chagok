package com.chagokchagok.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chagokchagok.app.ui.components.GhostButton
import com.chagokchagok.app.ui.components.StampButton
import com.chagokchagok.app.ui.theme.Eyebrow
import com.chagokchagok.app.ui.theme.Palette

/**
 * Nudge to start the car's navigation before driving off. Shown while parked, after the check-in has
 * finished, only when the app fills the screen (in a split the other window is usually the navigation).
 * "추천 보기" opens the next-stamp recommendations; both buttons hide it for the rest of the day.
 */
@Composable
fun NaviPromptCard(onOpenNavi: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(Palette.Card, RoundedCornerShape(20.dp))
      .padding(horizontal = 28.dp, vertical = 24.dp),
  ) {
    Text("NEXT STAMP", style = Eyebrow, color = Palette.Primary)
    Spacer(Modifier.height(6.dp))
    Text("다음 도장은 내비로 찾아가요", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
    Spacer(Modifier.height(6.dp))
    Text(
      "가까운, 아직 도장이 없는 곳을 골라 내비로 안내받으세요. 도착해서 주차하면 그곳에 도장을 찍어요.",
      style = MaterialTheme.typography.bodyMedium,
      color = Palette.TextSecondary,
    )
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      GhostButton("괜찮아요", onDismiss, Modifier.weight(1f))
      StampButton("추천 보기", onOpenNavi, Modifier.weight(1.4f))
    }
  }
}
