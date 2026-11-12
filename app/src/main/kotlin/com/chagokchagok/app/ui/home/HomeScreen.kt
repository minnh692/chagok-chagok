package com.chagokchagok.app.ui.home

import com.chagokchagok.app.ui.components.enterOnce
import com.chagokchagok.app.ui.components.animatedCount
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.chagokchagok.app.ui.components.GhostButton
import com.chagokchagok.app.ui.components.MapLegend
import com.chagokchagok.app.ui.components.StatusPill
import com.chagokchagok.app.ui.components.eyebrowDate
import com.chagokchagok.app.ui.map.fillColors
import com.chagokchagok.app.ui.map.todayPins
import com.chagokchagok.app.ui.theme.Eyebrow
import com.chagokchagok.app.ui.today.NaviPromptCard
import java.time.LocalDate
import com.chagokchagok.app.collection.Collections
import com.chagokchagok.app.navi.MissedStamp
import com.chagokchagok.app.ui.today.MissedStampsCard
import com.chagokchagok.app.collection.CollectionProgress
import com.chagokchagok.app.consent.LocationConsent
import com.chagokchagok.app.ui.Completion
import com.chagokchagok.app.ui.today.CollectionChips
import com.chagokchagok.app.ui.today.CompletionCard
import com.chagokchagok.app.ui.today.startedCollections
import com.chagokchagok.app.data.CompanionType
import com.chagokchagok.app.data.StampBook
import com.chagokchagok.app.region.RegionIndex
import com.chagokchagok.app.ui.CheckIn
import com.chagokchagok.app.ui.map.MapFilter
import com.chagokchagok.app.ui.map.MapGeometry
import com.chagokchagok.app.ui.map.MapTabs
import com.chagokchagok.app.ui.map.StampMap
import com.chagokchagok.app.ui.map.Arrival
import com.chagokchagok.app.ui.map.mapFills
import com.chagokchagok.app.ui.theme.Palette
import com.chagokchagok.app.ui.today.CheckInCard
import com.chagokchagok.app.ui.today.CompanionChips
import com.chagokchagok.app.ui.today.ConsentOffBanner
import com.chagokchagok.app.ui.today.TodayStampList

/**
 * Home = map + today panel. Wide (both windows, ~1682x1210 px): map left, panel right.
 * Narrow (split, ~841x1210 px): map on top, panel as a bottom sheet.
 */
@Composable
fun HomeScreen(
  regions: RegionIndex?,
  geometry: MapGeometry?,
  book: StampBook,
  consent: LocationConsent,
  checkIn: CheckIn,
  filter: MapFilter,
  naviPrompt: Boolean,
  onFilter: (MapFilter) -> Unit,
  onCompanion: (CompanionType?) -> Unit,
  onTurnOnLocation: () -> Unit,
  onRetry: () -> Unit,
  onRequestPermission: () -> Unit,
  onOpenNavi: () -> Unit,
  onDismissNavi: () -> Unit,
  onSaveMemo: (stampId: Long, memo: String) -> Unit,
  collections: Collections?,
  completions: List<Completion>,
  onDismissCompletion: (String) -> Unit,
  onOpenMyMap: () -> Unit,
  arrival: Arrival?,
  onArrivalDone: () -> Unit,
  restProgress: CollectionProgress?,
  missed: List<MissedStamp>,
  onStampMissed: (MissedStamp, LocalDate) -> Unit,
  onDismissMissed: (MissedStamp) -> Unit,
) {
  val progress = remember(book, collections, restProgress) { collections?.progress(book).orEmpty() + listOfNotNull(restProgress) }
  val fillsByCode = remember(book, filter) { mapFills(book, filter) }
  val fills = remember(fillsByCode) { fillColors(fillsByCode) }
  // Today's route belongs to "my map"; companion tabs only show which regions were shared.
  val pins = remember(book, filter, regions) {
    if (filter == MapFilter.MINE) todayPins(book, regions) else emptyList()
  }
  val regionCount = regions?.size ?: TOTAL_REGIONS
  val today = remember(book.today) { runCatching { LocalDate.parse(book.today) }.getOrDefault(LocalDate.now()) }

  val map = @Composable { modifier: Modifier, topInset: Dp ->
    Box(modifier) {
      // The arrival plays on "my map" only.
      StampMap(
        geometry, fills, pins, Modifier.fillMaxSize(), topInset = topInset, bottomInset = 96.dp,
        arrival = arrival.takeIf { filter == MapFilter.MINE }, onArrivalDone = onArrivalDone,
      )
      // Keeps the header readable over the map.
      Box(
        Modifier
          .fillMaxWidth()
          .height(220.dp)
          .background(
            Brush.verticalGradient(
              0f to Palette.MapShade.copy(alpha = 0.97f),
              0.5f to Palette.MapShade.copy(alpha = 0.8f),
              1f to Palette.MapShade.copy(alpha = 0f),
            ),
          ),
      )
      MapHeader(fills.size, regionCount, filter, today, Modifier.padding(28.dp))
      StatusPill("주차 중", dot = Palette.Primary, modifier = Modifier.align(Alignment.TopEnd).padding(28.dp))
      Column(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.End,
      ) {
        if (fills.isNotEmpty()) {
          MapLegend()
          Spacer(Modifier.height(12.dp))
        }
        MapTabs(
          selected = filter,
          onSelect = onFilter,
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .horizontalScroll(rememberScrollState()),
        )
      }
    }
  }
  val panel = @Composable { modifier: Modifier, wide: Boolean ->
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
      if (consent == LocationConsent.GRANTED) {
        CheckInCard(
          checkIn, book, regions,
          onRetry = onRetry, onRequestPermission = onRequestPermission, onSaveMemo = onSaveMemo,
        )
      } else {
        ConsentOffBanner(onTurnOn = onTurnOnLocation)
      }
      if (missed.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        MissedStampsCard(missed, regions, today, onStampMissed, onDismissMissed, Modifier.enterOnce("missed"))
      }
      completions.firstOrNull()?.let { c ->
        Spacer(Modifier.height(16.dp))
        CompletionCard(c, onDismiss = { onDismissCompletion(c.id) }, modifier = Modifier.enterOnce(c.id))
      }
      // In a split the other window is usually the navigation already, so the nudge is only for full width.
      if (naviPrompt && wide) {
        Spacer(Modifier.height(16.dp))
        NaviPromptCard(onOpenNavi = onOpenNavi, onDismiss = onDismissNavi, modifier = Modifier.enterOnce("navi", delayMs = 150))
      }
      Spacer(Modifier.height(28.dp))
      TodayStampList(book, regions)
      Spacer(Modifier.height(16.dp))
      GhostButton("나의 지도 크게 보기", onOpenMyMap, Modifier.fillMaxWidth())
      val started = remember(progress) { startedCollections(progress) }
      if (started.isNotEmpty()) {
        Spacer(Modifier.height(28.dp))
        CollectionChips(started)
      }
      Spacer(Modifier.height(28.dp))
      CompanionChips(
        selected = book.todayCompanion,
        enabled = book.todayStamps.isNotEmpty(),
        onSelect = onCompanion,
      )
    }
  }

  BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Panel)) {
    val panelWidth = maxWidth * 0.38f
    if (maxWidth > maxHeight) {
      Row(Modifier.fillMaxSize()) {
        map(Modifier.weight(1f).fillMaxHeight(), 120.dp)
        panel(Modifier.width(panelWidth).fillMaxHeight().background(Palette.Panel), true)
      }
    } else {
      Column(Modifier.fillMaxSize()) {
        map(Modifier.fillMaxWidth().weight(0.55f), 120.dp)
        panel(
          Modifier
            .fillMaxWidth()
            .weight(0.45f)
            .background(Palette.Panel, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
          false,
        )
      }
    }
  }
}

@Composable
private fun MapHeader(count: Int, regionCount: Int, filter: MapFilter, today: LocalDate, modifier: Modifier = Modifier) {
  val scope = when (filter.companion) {
    null -> "전국 시·군·구"
    CompanionType.FAMILY -> "가족과 함께, 전국"
    CompanionType.PARTNER -> "연인과 함께, 전국"
    CompanionType.FRIEND -> "친구와 함께, 전국"
    CompanionType.ALONE -> "혼자서, 전국"
  }
  Column(modifier) {
    Text("${eyebrowDate(today)}  ·  CHAGOK CHAGOK", style = Eyebrow, color = Palette.Primary)
    Spacer(Modifier.height(4.dp))
    Text("${animatedCount(count)} STAMPS", style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 1.sp), color = Palette.Text)
    Spacer(Modifier.height(4.dp))
    Text(
      "$scope ${regionCount}곳 중 ${count}곳",
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
      color = Palette.TextSecondary,
    )
  }
}

private const val TOTAL_REGIONS = 230
