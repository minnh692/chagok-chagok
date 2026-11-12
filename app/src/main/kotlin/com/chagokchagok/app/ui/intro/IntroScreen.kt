package com.chagokchagok.app.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.chagokchagok.app.R
import com.chagokchagok.app.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Text on the paper: the app's panel color, so the hand-over to the dark panel reads as one ink. */
private val Ink = Palette.Panel

/** The driving state arrives from the car service shortly after start; wait this long before deciding. */
private const val STATE_WAIT_MS = 700L
private const val HOLD_MS = 500L
private const val STILL_MS = 900L

/**
 * Launch intro: the 차곡차곡 stamp is pressed onto a sheet of paper, then the paper fades into the app.
 *
 * Nothing moves unless the car is known to be parked. While driving (or while the state is still unknown
 * after [STATE_WAIT_MS]) the logo is shown still and handed over to the driving notice; if driving starts
 * during the animation the intro ends at once. A tap skips it while parked.
 */
@Composable
fun IntroScreen(drivingRestricted: Boolean, onFinished: () -> Unit) {
  // Paper of the logo stamp (design/logo/), shared with the launcher icon and the system splash.
  val paper = colorResource(R.color.chagok_paper)
  val restricted by rememberUpdatedState(drivingRestricted)
  val finish by rememberUpdatedState(onFinished)

  val scale = remember { Animatable(1.35f) }
  val tilt = remember { Animatable(-8f) }
  val stampAlpha = remember { Animatable(0f) }
  val tagAlpha = remember { Animatable(0f) }
  val fade = remember { Animatable(0f) } // 0 = paper, 1 = app panel
  var animating by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    val parked = withTimeoutOrNull(STATE_WAIT_MS) { snapshotFlow { restricted }.first { !it } } != null
    if (parked) {
      animating = true
      launch { stampAlpha.animateTo(1f, tween(120)) }
      launch { tilt.animateTo(0f, tween(260, easing = FastOutLinearInEasing)) }
      scale.animateTo(0.95f, tween(260, easing = FastOutLinearInEasing))
      scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
      tagAlpha.animateTo(1f, tween(240))
      delay(HOLD_MS)
      fade.animateTo(1f, tween(260))
      animating = false
    } else {
      scale.snapTo(1f)
      tilt.snapTo(0f)
      stampAlpha.snapTo(1f)
      tagAlpha.snapTo(1f)
      delay(STILL_MS)
    }
    finish()
  }

  LaunchedEffect(drivingRestricted, animating) {
    if (drivingRestricted && animating) finish()
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .drawBehind { drawRect(lerp(paper, Palette.Panel, fade.value)) }
      // A tap skips the intro, but only while parked: nothing accepts input while restricted.
      .pointerInput(Unit) { detectTapGestures(onTap = { if (!restricted) finish() }) },
    contentAlignment = Alignment.Center,
  ) {
    val stampSize = minOf(maxWidth, maxHeight) * 0.42f
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.graphicsLayer { alpha = 1f - fade.value },
    ) {
      Image(
        painter = painterResource(R.drawable.chagok_stamp),
        contentDescription = stringResource(R.string.app_name),
        modifier = Modifier
          .size(stampSize)
          .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            rotationZ = tilt.value
            alpha = stampAlpha.value
          },
      )
      Spacer(Modifier.height(stampSize * 0.12f))
      Text(
        text = "차를 세운 곳마다 도장 하나씩",
        color = Ink,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
        modifier = Modifier.graphicsLayer { alpha = tagAlpha.value },
      )
    }
  }
}
