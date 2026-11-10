package com.chagokchagok.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Location from Android's LocationManager (GPS provider). Receives emulator virtual GPS. */
class AndroidLocationSource(context: Context) : LocationSource {
  private val appContext = context.applicationContext
  private val locationManager = appContext.getSystemService(LocationManager::class.java)
  private val listener = LocationListenerCompat { location -> publish(location) }
  private var listening = false

  override val name = "LocationManager"
  private val _status = MutableStateFlow<SourceStatus>(SourceStatus.Idle)
  override val status: StateFlow<SourceStatus> = _status
  private val _latest = MutableStateFlow<LocationFix?>(null)
  override val latest: StateFlow<LocationFix?> = _latest

  @SuppressLint("MissingPermission")
  override fun start() {
    if (listening) return
    if (!hasPermission()) {
      _status.value = SourceStatus.Failed("location permission not granted")
      return
    }
    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::publish)
    val request = LocationRequestCompat.Builder(UPDATE_INTERVAL_MS)
      .setQuality(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
      .build()
    LocationManagerCompat.requestLocationUpdates(
      locationManager,
      LocationManager.GPS_PROVIDER,
      request,
      ContextCompat.getMainExecutor(appContext),
      listener,
    )
    listening = true
    _status.value = SourceStatus.Ready
  }

  @SuppressLint("MissingPermission")
  override fun refresh() {
    if (!hasPermission()) return
    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::publish)
  }

  override fun stop() {
    if (!listening) return
    LocationManagerCompat.removeUpdates(locationManager, listener)
    listening = false
    _status.value = SourceStatus.Idle
  }

  private fun publish(location: Location) {
    _latest.value = LocationFix(location.latitude, location.longitude, timestampMillis = location.time)
  }

  private fun hasPermission() =
    ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED

  private companion object {
    const val UPDATE_INTERVAL_MS = 5_000L
  }
}
