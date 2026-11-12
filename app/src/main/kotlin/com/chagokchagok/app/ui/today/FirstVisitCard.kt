package com.chagokchagok.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chagokchagok.app.data.CompanionType
import com.chagokchagok.app.data.StampEntity
import com.chagokchagok.app.data.StampRepository
import com.chagokchagok.app.region.Region
import com.chagokchagok.app.ui.components.GhostButton
import com.chagokchagok.app.ui.components.StampButton
import com.chagokchagok.app.ui.components.eyebrowDate
import com.chagokchagok.app.ui.theme.MinTouch
import com.chagokchagok.app.ui.theme.Palette
import java.time.LocalDate

/**
 * B3 "first visit": shown as the check-in result only when this stamp is the first ever in the region.
 * Region + sido, date, "n번째 도장" (total), the companion if one is chosen, and an optional one-line memo.
 *
 * @param stamp the stored stamp (live, so the companion and memo follow what is saved).
 * @param total distinct regions stamped, including this one.
 * @param extraStat optional third stat, e.g. sido progress ("경기도" to "12 / 31").
 */
@Composable
fun FirstVisitCard(
  stamp: StampEntity,
  region: Region,
  total: Int,
  extraStat: Pair<String, String>?,
  onSaveMemo: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val date = remember(stamp.date) { runCatching { LocalDate.parse(stamp.date) }.getOrNull() }
  val companion = CompanionType.fromKey(stamp.companion)
  val stored = stamp.memo.orEmpty()
  var memo by remember(stamp.id) { mutableStateOf(stored) }
  val focus = LocalFocusManager.current
  val dirty = (StampRepository.cleanMemo(memo) ?: "") != stored

  Column(modifier.fillMaxWidth().background(Palette.Card, RoundedCornerShape(20.dp)).padding(20.dp)) {
    // Arrival card: white, red bar on the left.
    Row(
      Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min)
        .background(Palette.OnPrimary, RoundedCornerShape(14.dp)),
    ) {
      Box(Modifier.width(8.dp).fillMaxHeight().background(Palette.Primary, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)))
      Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
        Text(
          "FIRST STAMP" + (date?.let { "  ·  ${eyebrowDate(it)}" } ?: ""),
          color = Palette.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
        )
        Text(
          region.name, color = Palette.MapLabelText, fontSize = 40.sp, lineHeight = 46.sp,
          fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(region.sido, color = Palette.MapLabelSub, fontSize = 20.sp, fontWeight = FontWeight.Medium)
      }
    }

    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      if (companion != null) Stat("함께한 사람", companion.label, Modifier.weight(1f))
      Stat("나의 도장", "${total}번째", Modifier.weight(1f))
      extraStat?.let { Stat(it.first, it.second, Modifier.weight(1f)) }
    }

    Spacer(Modifier.height(18.dp))
    Text("한 줄 메모 (선택)", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
    Spacer(Modifier.height(10.dp))
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = MinTouch)
        .background(Palette.Panel, RoundedCornerShape(14.dp))
        .border(1.5.dp, Palette.Outline, RoundedCornerShape(14.dp))
        .padding(horizontal = 20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.weight(1f)) {
        if (memo.isEmpty()) {
          Text("예: 두물머리 산책, 핫도그 맛집", style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary)
        }
        BasicTextField(
          value = memo,
          onValueChange = { memo = it.replace('\n', ' ').take(StampRepository.MEMO_MAX) },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
          cursorBrush = SolidColor(Palette.Primary),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
          modifier = Modifier.fillMaxWidth(),
        )
      }
      Spacer(Modifier.width(12.dp))
      Text("${memo.length}/${StampRepository.MEMO_MAX}", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
    }
    Spacer(Modifier.height(14.dp))
    if (dirty) {
      StampButton("저장", { onSaveMemo(memo); focus.clearFocus() }, Modifier.fillMaxWidth())
    } else {
      GhostButton(if (stored.isEmpty()) "저장" else "저장했어요", { focus.clearFocus() }, Modifier.fillMaxWidth())
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
    Text(value, style = MaterialTheme.typography.titleLarge, color = Palette.Text, maxLines = 1)
  }
}
