package com.chagokchagok.app.ui.consent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chagokchagok.app.ui.theme.MinTouch
import com.chagokchagok.app.ui.theme.Palette

/** First-run location consent: what is stored and what is not. */
@Composable
fun ConsentScreen(onAccept: () -> Unit, onLater: () -> Unit) {
  BoxWithConstraints(
    modifier = Modifier.fillMaxSize().background(Palette.Panel),
    contentAlignment = Alignment.Center,
  ) {
    val wide = maxWidth > 700.dp
    Column(
      modifier = Modifier
        .widthIn(max = 960.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 40.dp, vertical = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text("차곡차곡", style = MaterialTheme.typography.titleMedium, color = Palette.Primary)
      Spacer(Modifier.height(12.dp))
      Text(
        "머문 시·군·구에 도장을 찍으려면\n위치를 한 번씩 확인해야 해요",
        style = MaterialTheme.typography.headlineMedium,
        color = Palette.Text,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(32.dp))

      val stored = @Composable { m: Modifier ->
        InfoCard(m, title = "저장하는 것", mark = "✓", markColor = Color(0xFF8FD18F), items = listOf("시·군·구 이름", "날짜", "그날 순서"))
      }
      val notStored = @Composable { m: Modifier ->
        InfoCard(m, title = "저장하지 않는 것", mark = "✕", markColor = Palette.Primary, items = listOf("정확한 좌표", "이동 경로", "시각"))
      }
      if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
          stored(Modifier.weight(1f))
          notStored(Modifier.weight(1f))
        }
      } else {
        stored(Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        notStored(Modifier.fillMaxWidth())
      }

      Spacer(Modifier.height(20.dp))
      Text(
        "기록은 이 차 안에만 저장되고 어디로도 보내지 않아요. 위치는 주차 중 앱을 열 때만 확인해요.",
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.TextSecondary,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(32.dp))

      Button(
        onClick = onAccept,
        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Primary, contentColor = Palette.OnPrimary),
      ) { Text("위치 사용 동의하고 시작", style = MaterialTheme.typography.labelLarge) }
      Spacer(Modifier.height(16.dp))
      OutlinedButton(
        onClick = onLater,
        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Text),
      ) { Text("나중에", style = MaterialTheme.typography.labelLarge) }
    }
  }
}

@Composable
private fun InfoCard(modifier: Modifier, title: String, mark: String, markColor: Color, items: List<String>) {
  Column(
    modifier = modifier
      .background(Palette.Card, RoundedCornerShape(20.dp))
      .padding(28.dp),
  ) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.TextSecondary)
    Spacer(Modifier.height(16.dp))
    items.forEach { item ->
      Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.widthIn(min = 40.dp)) {
          Text(mark, style = MaterialTheme.typography.titleLarge, color = markColor)
        }
        Text(item, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
      }
    }
  }
}
