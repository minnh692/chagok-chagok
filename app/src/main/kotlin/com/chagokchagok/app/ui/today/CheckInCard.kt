package com.chagokchagok.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chagokchagok.app.data.StampResult
import com.chagokchagok.app.ui.CheckIn
import com.chagokchagok.app.ui.theme.MinTouch
import com.chagokchagok.app.ui.theme.Palette

/** Banner shown on the today screen while location consent is declined. */
@Composable
fun ConsentOffBanner(onTurnOn: () -> Unit, modifier: Modifier = Modifier) {
  StatusCard(
    modifier = modifier,
    title = "위치 사용이 꺼져 있어요",
    body = "동의하면 주차할 때마다 그곳의 시·군·구에 도장을 찍어요.",
    action = "위치 사용 켜기" to onTurnOn,
  )
}

/** The result of the last check-in: STAMPED!, ALREADY STAMPED, WELCOME BACK, or why nothing happened. */
@Composable
fun CheckInCard(
  checkIn: CheckIn,
  onRetry: () -> Unit,
  onRequestPermission: () -> Unit,
  modifier: Modifier = Modifier,
) {
  when (checkIn) {
    CheckIn.Idle -> StatusCard(modifier, "주차하면 도장을 찍어요", "앱을 열 때 지금 있는 시·군·구를 확인해요.")
    CheckIn.Locating -> StatusCard(modifier, "어디인지 확인하고 있어요", "잠시만 기다려 주세요.")
    CheckIn.OutsideRegions -> StatusCard(
      modifier, "시·군·구를 찾지 못했어요", "바다 위나 국외로 확인됐어요. 육지에 주차한 뒤 다시 확인해 주세요.",
      action = "다시 확인" to onRetry,
    )
    CheckIn.LocationUnavailable -> StatusCard(
      modifier, "위치를 확인하지 못했어요", "위치 신호를 받지 못했어요. 잠시 후 다시 확인해 주세요.",
      action = "다시 확인" to onRetry,
    )
    CheckIn.PermissionNeeded -> StatusCard(
      modifier, "위치 권한이 필요해요", "도장을 찍으려면 차량 설정에서 위치 권한을 허용해 주세요.",
      action = "권한 허용" to onRequestPermission,
    )
    is CheckIn.Done -> ResultCard(checkIn, modifier)
  }
}

@Composable
private fun ResultCard(done: CheckIn.Done, modifier: Modifier) {
  val region = done.region.fullName
  val (badge, color, line) = when (val r = done.result) {
    is StampResult.New -> Triple("STAMPED!", Palette.StampedToday, "오늘 ${r.newToday} NEW STAMPS")
    is StampResult.AlreadyStamped -> Triple("ALREADY STAMPED", Palette.TextSecondary, "오늘 이미 찍은 도장이에요")
    is StampResult.WelcomeBack -> Triple("WELCOME BACK", Palette.StampedBefore, "${r.visitCount}번째 방문이에요")
  }
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(Palette.Card, RoundedCornerShape(20.dp))
      .padding(horizontal = 28.dp, vertical = 24.dp),
  ) {
    Text(badge, color = color, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(8.dp))
    Text(region, style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
    Spacer(Modifier.height(4.dp))
    Text(line, style = MaterialTheme.typography.titleMedium, color = Palette.TextSecondary)
  }
}

@Composable
private fun StatusCard(
  modifier: Modifier,
  title: String,
  body: String,
  action: Pair<String, () -> Unit>? = null,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(Palette.Card, RoundedCornerShape(20.dp))
      .padding(horizontal = 28.dp, vertical = 24.dp),
  ) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
    Spacer(Modifier.height(6.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
    if (action != null) {
      Spacer(Modifier.height(16.dp))
      Button(
        onClick = action.second,
        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Primary, contentColor = Color.White),
      ) { Text(action.first, style = MaterialTheme.typography.labelLarge) }
    }
  }
}
