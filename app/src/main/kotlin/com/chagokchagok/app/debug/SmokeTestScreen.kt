package com.chagokchagok.app.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chagokchagok.app.location.LocationFix
import com.chagokchagok.app.location.SourceStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Temporary environment smoke test. Not the real UI. */
@Composable
fun SmokeTestScreen(
  androidStatus: SourceStatus,
  androidFix: LocationFix?,
  naviStatus: SourceStatus,
  naviFix: LocationFix?,
  ignition: String,
  onRefresh: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(48.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Text("Smoke test (debug)", style = MaterialTheme.typography.headlineLarge)
    Section("A. LocationManager", "status: ${androidStatus.label()}\nlatest: ${androidFix.label()}")
    Section("B. NaviHelper", "init: ${naviStatus.label()}\ncurrent location: ${naviFix.label()}")
    Section("C. Ignition", ignition)
    Button(onClick = onRefresh) { Text("Refresh") }
  }
}

@Composable
private fun Section(title: String, body: String) {
  Column {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(body, style = MaterialTheme.typography.bodyLarge)
  }
}

private fun SourceStatus.label() = when (this) {
  SourceStatus.Idle -> "idle"
  SourceStatus.Connecting -> "connecting"
  SourceStatus.Ready -> "ready"
  is SourceStatus.Failed -> "failed - $reason"
}

private fun LocationFix?.label(): String {
  if (this == null) return "none"
  val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestampMillis))
  val addressPart = address?.let { "  address=\"$it\"" } ?: ""
  return "%.5f, %.5f  @%s%s".format(Locale.US, latitude, longitude, time, addressPart)
}
