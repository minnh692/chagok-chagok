package com.chagokchagok.app.navi

/** Where to send the car's navigation: a region's label point and its name. Never stored. */
data class RouteTarget(val name: String, val address: String, val latitude: Double, val longitude: Double)

enum class RouteResult {
  /** The navigation accepted the destination (route searched or guidance started). */
  STARTED,

  /**
   * The request reached the navigation but no event came back within [RouteRequester.SENT_GRACE_MS].
   * The emulator navigation opens its route search this way and never answers (verified 2026-11-12,
   * NaviHelper service 3.0.4), so this counts as delivered.
   */
  SENT,

  /** The navigation reported an error or could not be reached. */
  FAILED,

  /** NaviHelper did not even connect within [RouteRequester.TIMEOUT_MS]; nothing was sent. */
  TIMED_OUT,
}

/** Hands a destination to the car's navigation. The only way the app talks to NaviHelper for routes. */
interface RouteRequester {
  suspend fun requestRoute(target: RouteTarget): RouteResult

  companion object {
    const val TIMEOUT_MS = 5_000L
    const val SENT_GRACE_MS = 1_500L
  }
}
