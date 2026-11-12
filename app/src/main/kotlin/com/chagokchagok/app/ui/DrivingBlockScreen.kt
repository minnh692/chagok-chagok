package com.chagokchagok.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chagokchagok.app.ui.theme.Palette

/**
 * Shown instead of the whole UI while driving restrictions are active.
 * Static on purpose: no buttons, no animation, nothing to read twice.
 */
@Composable
fun DrivingBlockScreen() {
  Column(
    modifier = Modifier.fillMaxSize().background(Palette.Panel).padding(48.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      "주행 중에는 사용할 수 없어요",
      style = MaterialTheme.typography.headlineMedium,
      color = Palette.Text,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Text(
      "주차(P)하면 오늘의 도장을 보여드릴게요",
      style = MaterialTheme.typography.titleMedium,
      color = Palette.TextSecondary,
      textAlign = TextAlign.Center,
    )
  }
}
