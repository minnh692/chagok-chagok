package com.chagokchagok.app.location

import kotlinx.coroutines.flow.StateFlow

/** A single location reading. Raw coordinates stay in memory only and are never persisted. */
data class LocationFix(
  val latitude: Double,
  val longitude: Double,
  /** Human-readable address when the source provides one (NaviHelper does). */
  val address: String? = null,
  val timestampMillis: Long = System.currentTimeMillis(),
)

/** Connection / availability state of a [LocationSource]. */
sealed interface SourceStatus {
  data object Idle : SourceStatus
  data object Connecting : SourceStatus
  data object Ready : SourceStatus
  data class Failed(val reason: String) : SourceStatus
}

/**
 * The one interface the app reads location through.
 * NaviHelper on real cars, LocationManager on the emulator (built-in navigation ignores virtual GPS there).
 */
interface LocationSource {
  val name: String
  val status: StateFlow<SourceStatus>
  val latest: StateFlow<LocationFix?>

  /** Begin receiving updates. Call from onStart. */
  fun start()

  /** Ask the source for a fresh reading (no-op for push-based sources). */
  fun refresh()

  /** Stop updates. Call from onStop. */
  fun stop()
}
