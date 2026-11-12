package com.chagokchagok.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chagokchagok.app.ui.theme.MinTouch
import com.chagokchagok.app.ui.theme.Palette

/** Tabs over the map: 나의 지도 / 우리 가족 / 연인 / 친구. */
@Composable
fun MapTabs(selected: MapFilter, onSelect: (MapFilter) -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .background(Palette.Panel.copy(alpha = 0.92f), RoundedCornerShape(40.dp))
      .padding(6.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    MapFilter.entries.forEach { filter ->
      val isSelected = filter == selected
      Box(
        modifier = Modifier
          .heightIn(min = MinTouch)
          .widthIn(min = 120.dp)
          .background(if (isSelected) Palette.Primary else Palette.Panel.copy(alpha = 0f), RoundedCornerShape(36.dp))
          .semantics { this.selected = isSelected }
          .clickable(role = Role.Tab) { onSelect(filter) }
          .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          filter.label,
          style = MaterialTheme.typography.labelLarge,
          color = if (isSelected) Palette.OnPrimary else Palette.Text,
        )
      }
    }
  }
}
