package com.chagokchagok.app.location

/**
 * A single location reading. It lives in memory only long enough to look up the si/gun/gu
 * and is never persisted or logged.
 */
data class LocationFix(val latitude: Double, val longitude: Double)

/** Outcome of one [LocationSource.readOnce] call. */
sealed interface LocationRead {
  data class Fix(val fix: LocationFix) : LocationRead

  /** The platform location permission is not granted. */
  data object PermissionMissing : LocationRead

  /** Location is off or no fix arrived in time. */
  data object Unavailable : LocationRead
}

/**
 * The one interface the app reads location through.
 * P0 uses [AndroidLocationSource] (LocationManager; follows `adb emu geo fix` on the emulator).
 * [NaviHelperLocationSource] is the planned real-car source and is a stub for now.
 *
 * Callers must check the user's location consent before calling [readOnce]; when consent is
 * declined the app never calls it.
 */
interface LocationSource {
  val name: String

  /** Reads the current location once. No updates continue after it returns. */
  suspend fun readOnce(): LocationRead
}
