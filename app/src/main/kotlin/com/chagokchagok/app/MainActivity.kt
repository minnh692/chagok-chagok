package com.chagokchagok.app

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import com.chagokchagok.app.ui.mymap.MyMapScreen
import com.chagokchagok.app.ui.next.NextStampScreen
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.chagokchagok.app.ui.theme.Palette
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chagokchagok.app.car.CarStateMonitor
import com.chagokchagok.app.ui.AppViewModel
import com.chagokchagok.app.ui.DrivingBlockScreen
import com.chagokchagok.app.ui.Screen
import com.chagokchagok.app.ui.consent.ConsentScreen
import com.chagokchagok.app.ui.home.HomeScreen
import com.chagokchagok.app.ui.map.MapFilter
import com.chagokchagok.app.ui.map.fillColors
import com.chagokchagok.app.ui.map.mapFills
import com.chagokchagok.app.ui.map.rememberMapGeometry
import com.chagokchagok.app.ui.theme.ChagokChagokTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val vm: AppViewModel by viewModels()
  private lateinit var carState: CarStateMonitor

  /** Set when the system no longer shows the permission dialog, so the next tap opens settings instead. */
  private var permissionBlocked = false

  // The permission dialog pauses this activity; onResume runs the check-in after it closes.
  private val permissionRequest =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      permissionBlocked = !granted && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    carState = CarStateMonitor(this)
    applyDebugDayOffset(intent)

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        carState.uxRestricted.collect { restricted ->
          val parkedNow = vm.setDrivingRestricted(restricted)
          if (parkedNow && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            vm.checkIn(hasLocationPermission())
          }
        }
      }
    }

    setContent {
      ChagokChagokTheme {
        val screen by vm.screen.collectAsStateWithLifecycle()
        val consent by vm.consent.collectAsStateWithLifecycle()
        val book by vm.book.collectAsStateWithLifecycle()
        val checkIn by vm.checkIn.collectAsStateWithLifecycle()
        val regions by vm.regions.collectAsStateWithLifecycle()
        val filter by vm.filter.collectAsStateWithLifecycle()
        val drivingRestricted by vm.drivingRestricted.collectAsStateWithLifecycle()
        val naviPrompt by vm.naviPrompt.collectAsStateWithLifecycle()
        val geometry = rememberMapGeometry(regions)
        val collections by vm.collections.collectAsStateWithLifecycle()
        val completions by vm.completions.collectAsStateWithLifecycle()
        val recommendations by vm.recommendations.collectAsStateWithLifecycle()
        val confirming by vm.confirming.collectAsStateWithLifecycle()
        val handoff by vm.handoff.collectAsStateWithLifecycle()
        val arrival by vm.arrival.collectAsStateWithLifecycle()
        val restProgress by vm.restProgress.collectAsStateWithLifecycle()
        val missed by vm.missed.collectAsStateWithLifecycle()

        BackHandler(enabled = screen != Screen.TODAY && !drivingRestricted) { vm.back() }
        // While driving, back does nothing either: the notice accepts no input at all.
        BackHandler(enabled = drivingRestricted) {}
        // While driving, the notice replaces everything; the screen state is kept and comes back on parking.
        if (drivingRestricted) {
          DrivingBlockScreen(geometry, fillColors(mapFills(book, MapFilter.MINE)))
          return@ChagokChagokTheme
        }
        // Screens slide in from the side they lead to (deeper to the right) and cross-fade.
        AnimatedContent(
          targetState = screen,
          modifier = Modifier.fillMaxSize().background(Palette.Panel),
          transitionSpec = {
            val dir = if (depth(targetState) >= depth(initialState)) 1 else -1
            (slideInHorizontally(tween(320)) { dir * it / 8 } + fadeIn(tween(260)))
              .togetherWith(slideOutHorizontally(tween(320)) { -dir * it / 12 } + fadeOut(tween(200)))
          },
          label = "screen",
        ) { shown ->
        when (shown) {
          Screen.CONSENT -> ConsentScreen(onAccept = ::acceptConsent, onLater = vm::declineConsent)
          Screen.TODAY -> HomeScreen(
            regions = regions,
            geometry = geometry,
            book = book,
            consent = consent,
            checkIn = checkIn,
            filter = filter,
            naviPrompt = naviPrompt,
            onFilter = vm::setFilter,
            onCompanion = vm::setTodayCompanion,
            onTurnOnLocation = vm::openConsent,
            onRetry = { vm.checkIn(hasLocationPermission()) },
            onRequestPermission = ::requestLocationPermission,
            onOpenNavi = { vm.dismissNaviPrompt(); vm.openNext() },
            onDismissNavi = vm::dismissNaviPrompt,
            onSaveMemo = vm::saveMemo,
            collections = collections,
            completions = completions,
            onDismissCompletion = vm::dismissCompletion,
            onOpenMyMap = vm::openMyMap,
            arrival = arrival,
            onArrivalDone = vm::arrivalShown,
            restProgress = restProgress,
            missed = missed,
            onStampMissed = vm::stampMissed,
            onDismissMissed = vm::dismissMissed,
          )
          Screen.MY_MAP -> MyMapScreen(
            regions = regions,
            geometry = geometry,
            book = book,
            filter = filter,
            collections = collections,
            onFilter = vm::setFilter,
            onBack = { vm.back() },
            onOpenNext = vm::openNext,
            restProgress = restProgress,
          )
          Screen.NEXT -> NextStampScreen(
            geometry = geometry,
            book = book,
            recommendations = recommendations,
            confirming = confirming,
            handoff = handoff,
            onConfirm = vm::confirmNext,
            onStart = { code -> vm.startRoute(code, ::openNavigationApp) },
            onBack = { vm.back() },
          )
        }
        }
      }
    }
  }

  private fun depth(screen: Screen) = when (screen) {
    Screen.CONSENT, Screen.TODAY -> 0
    Screen.MY_MAP -> 1
    Screen.NEXT -> 2
  }

  override fun onStart() {
    super.onStart()
    carState.start()
  }

  override fun onResume() {
    super.onResume()
    vm.naviAvailable.value = packageManager.resolveActivity(navigationIntent(), 0) != null
    vm.checkIn(hasLocationPermission())
  }

  override fun onStop() {
    carState.stop()
    super.onStop()
  }

  private fun acceptConsent() {
    vm.acceptConsent()
    if (hasLocationPermission()) vm.checkIn(true) else requestLocationPermission()
  }

  private fun requestLocationPermission() {
    if (!permissionBlocked) {
      permissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
      return
    }
    runCatching {
      startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
      )
    }
  }

  /** The car's navigation app, found by the standard maps category (no package name is assumed). */
  private fun navigationIntent(): Intent =
    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS)

  // Its own task, so the navigation does not stack on top of ours (reopening Chagok Chagok must show us).
  private fun openNavigationApp() {
    runCatching { startActivity(navigationIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
  }

  private fun hasLocationPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED

  /**
   * Debug builds only: `am start -S -n com.chagokchagok.app/.MainActivity --el debug_day_offset 1`
   * pretends today is one day later, to test WELCOME BACK on the emulator (the user build cannot set the clock).
   */
  private fun applyDebugDayOffset(intent: Intent?) {
    val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (debuggable && intent?.hasExtra(EXTRA_DAY_OFFSET) == true) {
      vm.dayOffset = intent.getLongExtra(EXTRA_DAY_OFFSET, 0)
    }
  }

  private companion object {
    const val EXTRA_DAY_OFFSET = "debug_day_offset"
  }
}
