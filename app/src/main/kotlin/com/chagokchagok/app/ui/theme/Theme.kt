package com.chagokchagok.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Minimum touch target for in-car use. */
val MinTouch = 76.dp

private val ColorScheme = darkColorScheme(
  primary = Palette.Primary,
  onPrimary = Palette.OnPrimary,
  background = Palette.Panel,
  onBackground = Palette.Text,
  surface = Palette.Panel,
  onSurface = Palette.Text,
  surfaceVariant = Palette.Card,
  onSurfaceVariant = Palette.TextSecondary,
  outline = Palette.Divider,
)

@Composable
fun ChagokChagokTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = ColorScheme, typography = Typography, content = content)
}
