package com.chagokchagok.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** One-shot location from Android's LocationManager. Receives the emulator's virtual GPS. */
class AndroidLocationSource(context: Context) : LocationSource {
  private val appContext = context.applicationContext
  private val locationManager = appContext.getSystemService(LocationManager::class.java)

  override val name = "LocationManager"

  @SuppressLint("MissingPermission")
  override suspend fun readOnce(): LocationRead {
    if (!hasPermission()) return LocationRead.PermissionMissing
    // Logged without coordinates so a declined-consent run can be checked for zero reads.
    Log.i(TAG, "readOnce")
    if (!LocationManagerCompat.isLocationEnabled(locationManager)) return LocationRead.Unavailable
    val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
      .firstOrNull { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
      ?: return LocationRead.Unavailable

    val location = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) { currentLocation(provider) }
      ?: locationManager.getLastKnownLocation(provider)
    return location?.let { LocationRead.Fix(LocationFix(it.latitude, it.longitude)) } ?: LocationRead.Unavailable
  }

  @SuppressLint("MissingPermission")
  private suspend fun currentLocation(provider: String): Location? = suspendCancellableCoroutine { cont ->
    val signal = CancellationSignal()
    cont.invokeOnCancellation { signal.cancel() }
    LocationManagerCompat.getCurrentLocation(
      locationManager,
      provider,
      signal,
      ContextCompat.getMainExecutor(appContext),
    ) { location -> if (cont.isActive) cont.resume(location) }
  }

  private fun hasPermission() =
    ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED

  private companion object {
    const val TAG = "ChagokLocation"
    const val FRESH_FIX_TIMEOUT_MS = 8_000L
  }
}
