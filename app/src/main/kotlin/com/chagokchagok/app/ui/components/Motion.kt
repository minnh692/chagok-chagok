package com.chagokchagok.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity

/*
 * Small motion helpers for the parked UI. None of this runs while driving: the whole UI is replaced by the
 * static DrivingBlockScreen whenever CarUxRestrictions are active, so these composables are not even composed.
 */

/** Interaction state of a control: hovered (pointer over it) and pressed. */
class Interaction(val hovered: Boolean, val pressed: Boolean)

@Composable
fun InteractionSource.interaction(): Interaction {
  val hovered by collectIsHoveredAsState()
  val pressed by collectIsPressedAsState()
  return Interaction(hovered, pressed)
}

/** Springy scale: shrinks a little while pressed, grows slightly on hover. */
fun Modifier.pressScale(state: Interaction, pressed: Float = 0.95f, hovered: Float = 1.02f): Modifier = composed {
  val target = when {
    state.pressed -> pressed
    state.hovered -> hovered
    else -> 1f
  }
  val scale by animateFloatAsState(
    target,
    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
    label = "pressScale",
  )
  graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Fades and slides the element up the first time it is shown ([key] restarts it). [delayMs] staggers lists.
 * Rotation or recomposition with the same key does not replay it.
 */
fun Modifier.enterOnce(key: Any? = Unit, delayMs: Int = 0, offsetDp: Float = 18f): Modifier = composed {
  var played by rememberSaveable(key) { mutableStateOf(false) }
  val progress = remember(key) { Animatable(if (played) 1f else 0f) }
  LaunchedEffect(key) {
    if (!played) {
      progress.animateTo(1f, tween(durationMillis = 360, delayMillis = delayMs, easing = FastOutSlowInEasing))
      played = true
    }
  }
  val offsetPx = with(LocalDensity.current) { offsetDp.dp.toPx() }
  graphicsLayer {
    alpha = progress.value
    translationY = (1f - progress.value) * offsetPx
  }
}

/** Counts up (or down) to [target] instead of jumping, e.g. "12 STAMPS" -> "13 STAMPS". */
@Composable
fun animatedCount(target: Int): Int {
  val value by animateIntAsState(target, tween(durationMillis = 600, easing = FastOutSlowInEasing), label = "count")
  return value
}

/** A short bouncy pop each time [on] turns true (e.g. a chip being chosen). */
fun Modifier.popOn(on: Boolean): Modifier = composed {
  val scale = remember { Animatable(1f) }
  var previous by remember { mutableStateOf(on) }
  LaunchedEffect(on) {
    if (on && !previous) {
      scale.snapTo(1.12f)
      scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
    }
    previous = on
  }
  graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/**
 * A rubber-stamp landing: starts large and tilted, slams down with a bounce. Plays once per [key].
 */
fun Modifier.stampIn(key: Any?, rotation: Float = -8f): Modifier = composed {
  var played by rememberSaveable(key) { mutableStateOf(false) }
  val progress = remember(key) { Animatable(if (played) 1f else 0f) }
  LaunchedEffect(key) {
    if (!played) {
      progress.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
      played = true
    }
  }
  graphicsLayer {
    val p = progress.value
    val s = 1.8f - 0.8f * p
    scaleX = s
    scaleY = s
    alpha = (p * 2.5f).coerceAtMost(1f)
    rotationZ = rotation * (1f - p)
  }
}
