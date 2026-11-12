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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
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

        BackHandler(enabled = screen != Screen.TODAY && !drivingRestricted) { vm.back() }
        // While driving, the notice replaces everything; the screen state is kept and comes back on parking.
        if (drivingRestricted) {
          DrivingBlockScreen()
          return@ChagokChagokTheme
        }
        when (screen) {
          Screen.CONSENT -> ConsentScreen(onAccept = ::acceptConsent, onLater = vm::declineConsent)
          Screen.TODAY -> HomeScreen(
            regions = regions,
            book = book,
            consent = consent,
            checkIn = checkIn,
            filter = filter,
            onFilter = vm::setFilter,
            onCompanion = vm::setTodayCompanion,
            onTurnOnLocation = vm::openConsent,
            onRetry = { vm.checkIn(hasLocationPermission()) },
            onRequestPermission = ::requestLocationPermission,
          )
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    carState.start()
  }

  override fun onResume() {
    super.onResume()
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
