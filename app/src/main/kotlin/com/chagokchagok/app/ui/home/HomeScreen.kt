package com.chagokchagok.app.ui.home

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chagokchagok.app.consent.LocationConsent
import com.chagokchagok.app.data.CompanionType
import com.chagokchagok.app.data.StampBook
import com.chagokchagok.app.region.RegionIndex
import com.chagokchagok.app.ui.CheckIn
import com.chagokchagok.app.ui.map.Fill
import com.chagokchagok.app.ui.map.MapFilter
import com.chagokchagok.app.ui.map.MapGeometry
import com.chagokchagok.app.ui.map.MapTabs
import com.chagokchagok.app.ui.map.StampMap
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
  book: StampBook,
  consent: LocationConsent,
  checkIn: CheckIn,
  filter: MapFilter,
  onFilter: (MapFilter) -> Unit,
  onCompanion: (CompanionType?) -> Unit,
  onTurnOnLocation: () -> Unit,
  onRetry: () -> Unit,
  onRequestPermission: () -> Unit,
) {
  val geometry by produceState<MapGeometry?>(null, regions) {
    value = regions?.let { MapGeometry.of(it) }
  }
  val fillsByCode = remember(book, filter) { mapFills(book, filter) }
  val fills = remember(fillsByCode) {
    fillsByCode.mapValues { (_, fill) -> if (fill == Fill.TODAY) Palette.StampedToday else Palette.StampedBefore }
  }
  // The order line belongs to "my map"; companion tabs only show which regions were shared.
  val orderCodes = remember(book, filter) {
    if (filter == MapFilter.MINE) book.todayStamps.map { it.regionCode } else emptyList()
  }
  val regionCount = regions?.size ?: TOTAL_REGIONS

  val map = @Composable { modifier: Modifier, topInset: Dp ->
    Box(modifier) {
      StampMap(geometry, fills, orderCodes, Modifier.fillMaxSize(), topInset)
      MapHeader(fills.size, regionCount, filter, Modifier.padding(28.dp))
      MapTabs(
        selected = filter,
        onSelect = onFilter,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(16.dp)
          .horizontalScroll(rememberScrollState()),
      )
    }
  }
  val panel = @Composable { modifier: Modifier ->
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
      if (consent == LocationConsent.GRANTED) {
        CheckInCard(checkIn, onRetry = onRetry, onRequestPermission = onRequestPermission)
      } else {
        ConsentOffBanner(onTurnOn = onTurnOnLocation)
      }
      Spacer(Modifier.height(28.dp))
      TodayStampList(book, regions)
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
        map(Modifier.weight(1f).fillMaxHeight(), 0.dp)
        panel(Modifier.width(panelWidth).fillMaxHeight().background(Palette.Panel))
      }
    } else {
      Column(Modifier.fillMaxSize()) {
        map(Modifier.fillMaxWidth().weight(0.55f), 88.dp)
        panel(
          Modifier
            .fillMaxWidth()
            .weight(0.45f)
            .background(Palette.Panel, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        )
      }
    }
  }
}

@Composable
private fun MapHeader(count: Int, regionCount: Int, filter: MapFilter, modifier: Modifier = Modifier) {
  val scope = when (filter.companion) {
    null -> "전국 시·군·구"
    CompanionType.FAMILY -> "가족과 함께, 전국"
    CompanionType.PARTNER -> "연인과 함께, 전국"
    CompanionType.FRIEND -> "친구와 함께, 전국"
    CompanionType.ALONE -> "혼자서, 전국"
  }
  Column(modifier) {
    Text("$count STAMPS", style = MaterialTheme.typography.displaySmall, color = Palette.Text)
    Spacer(Modifier.height(4.dp))
    Text("$scope ${regionCount}곳 중 ${count}곳", style = MaterialTheme.typography.titleMedium, color = Palette.TextSecondary)
  }
}

private const val TOTAL_REGIONS = 230
