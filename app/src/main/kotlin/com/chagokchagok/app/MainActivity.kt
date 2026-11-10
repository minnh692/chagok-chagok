package com.chagokchagok.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chagokchagok.app.car.CarStateMonitor
import com.chagokchagok.app.debug.SmokeTestScreen
import com.chagokchagok.app.location.AndroidLocationSource
import com.chagokchagok.app.location.NaviHelperLocationSource
import com.chagokchagok.app.ui.theme.ChagokChagokTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private lateinit var androidLocation: AndroidLocationSource
  private lateinit var naviLocation: NaviHelperLocationSource
  private lateinit var carState: CarStateMonitor

  private val permissionRequest =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      // Re-run start so the LocationManager source picks up the new grant.
      androidLocation.stop()
      androidLocation.start()
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    androidLocation = AndroidLocationSource(this)
    naviLocation = NaviHelperLocationSource(this)
    carState = CarStateMonitor(this)

    permissionRequest.launch(
      arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
    )

    // Poll only while visible; nothing runs in the background.
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) {
          delay(REFRESH_INTERVAL_MS)
          refreshAll()
        }
      }
    }

    setContent {
      ChagokChagokTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          val uxRestricted by carState.uxRestricted.collectAsStateWithLifecycle()
          if (uxRestricted) {
            // Parked-only app: hide everything while driving restrictions are active.
            Box(contentAlignment = Alignment.Center) {
              Text("주차 후 이용해 주세요", style = MaterialTheme.typography.headlineMedium)
            }
          } else {
            val androidStatus by androidLocation.status.collectAsStateWithLifecycle()
            val androidFix by androidLocation.latest.collectAsStateWithLifecycle()
            val naviStatus by naviLocation.status.collectAsStateWithLifecycle()
            val naviFix by naviLocation.latest.collectAsStateWithLifecycle()
            val ignition by carState.ignition.collectAsStateWithLifecycle()
            SmokeTestScreen(androidStatus, androidFix, naviStatus, naviFix, ignition, onRefresh = ::refreshAll)
          }
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    carState.start()
    androidLocation.start()
    naviLocation.start()
  }

  override fun onStop() {
    naviLocation.stop()
    androidLocation.stop()
    carState.stop()
    super.onStop()
  }

  private fun refreshAll() {
    androidLocation.refresh()
    naviLocation.refresh()
    carState.refresh()
  }

  private companion object {
    const val REFRESH_INTERVAL_MS = 3_000L
  }
}
