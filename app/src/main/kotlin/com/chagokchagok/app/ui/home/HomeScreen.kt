package com.chagokchagok.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chagokchagok.app.consent.LocationConsent
import com.chagokchagok.app.data.StampBook
import com.chagokchagok.app.ui.CheckIn
import com.chagokchagok.app.ui.theme.Palette
import com.chagokchagok.app.ui.today.CheckInCard
import com.chagokchagok.app.ui.today.ConsentOffBanner

/** Home (today) screen. TODO(step 4): map canvas and wide/narrow layout. */
@Composable
fun HomeScreen(
  book: StampBook,
  consent: LocationConsent,
  checkIn: CheckIn,
  onTurnOnLocation: () -> Unit,
  onRetry: () -> Unit,
  onRequestPermission: () -> Unit,
) {
  Box(Modifier.fillMaxSize().background(Palette.Panel)) {
    Column(Modifier.padding(32.dp)) {
      Text("${book.total} STAMPS", style = MaterialTheme.typography.displaySmall, color = Palette.Text)
      Text("전국 시·군·구 230곳 중 ${book.total}곳", style = MaterialTheme.typography.titleMedium, color = Palette.TextSecondary)
      Spacer(Modifier.height(24.dp))
      if (consent == LocationConsent.GRANTED) {
        CheckInCard(checkIn, onRetry = onRetry, onRequestPermission = onRequestPermission)
      } else {
        ConsentOffBanner(onTurnOn = onTurnOnLocation)
      }
    }
  }
}
