package com.chagokchagok.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.chagokchagok.app.collection.CollectionProgress
import com.chagokchagok.app.ui.Completion
import com.chagokchagok.app.ui.components.StampButton
import com.chagokchagok.app.ui.theme.Eyebrow
import com.chagokchagok.app.ui.theme.Palette

/** Which progress chips the today panel shows: everything already started, most complete first. */
fun startedCollections(all: List<CollectionProgress>): List<CollectionProgress> =
  all.filter { it.done > 0 }.sortedWith(compareByDescending<CollectionProgress> { it.fraction }.thenBy { it.id })

/** "모으는 중": progress chips such as "경기도 12/31". Not interactive. */
@Composable
fun CollectionChips(progress: List<CollectionProgress>, modifier: Modifier = Modifier) {
  if (progress.isEmpty()) return
  Column(modifier.fillMaxWidth()) {
    Text("모으는 중", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      progress.forEach { p ->
        Column(
          Modifier
            .width(IntrinsicSize.Max)
            .background(Palette.Card, RoundedCornerShape(16.dp))
            .border(1.5.dp, if (p.complete) Palette.Primary else Palette.Outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
          Text(p.label, style = MaterialTheme.typography.labelMedium, color = Palette.Text)
          Spacer(Modifier.height(8.dp))
          ProgressBar(p.fraction, Modifier.fillMaxWidth().height(6.dp))
        }
      }
    }
  }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
  Box(modifier.clip(CircleShape).background(Palette.Panel)) {
    Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(Palette.Primary, CircleShape))
  }
}

/** Shown once when a collection completes; "확인" dismisses it. */
@Composable
fun CompletionCard(completion: Completion, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  val p = completion.progress
  val (title, body) = when {
    completion.seasonRegion != null ->
      "두 계절의 도장" to "${completion.seasonRegion.fullName}에 서로 다른 계절에 도장을 찍었어요."
    p == null -> "컬렉션 완성" to ""
    p.kind == CollectionProgress.Kind.SIDO -> "${p.title} 완성!" to "${p.title} ${p.target}곳을 모두 모았어요."
    p.kind == CollectionProgress.Kind.COAST -> "바다를 낀 시·군·구 완성!" to "바다를 낀 시·군·구 ${p.target}곳을 모두 모았어요."
    else -> "${p.title} ${p.target}곳 달성!" to "${p.title} ${p.target}곳에 도장을 찍었어요."
  }
  Column(
    modifier
      .fillMaxWidth()
      .background(Palette.Card, RoundedCornerShape(20.dp))
      .border(2.dp, Palette.Primary, RoundedCornerShape(20.dp))
      .padding(horizontal = 28.dp, vertical = 24.dp),
  ) {
    Text("COLLECTION COMPLETE", style = Eyebrow, color = Palette.Primary)
    Spacer(Modifier.height(6.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
    if (body.isNotEmpty()) {
      Spacer(Modifier.height(6.dp))
      Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
    }
    Spacer(Modifier.height(16.dp))
    StampButton("확인", onDismiss, Modifier.fillMaxWidth())
  }
}
