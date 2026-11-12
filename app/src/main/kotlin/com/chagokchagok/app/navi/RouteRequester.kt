package com.chagokchagok.app.navi

/** Where to send the car's navigation: a region's label point and its name. Never stored. */
data class RouteTarget(val name: String, val address: String, val latitude: Double, val longitude: Double)

enum class RouteResult {
  /** The navigation accepted the destination (route searched or guidance started). */
  STARTED,

  /** The navigation reported an error or could not be reached. */
  FAILED,

  /**
   * No answer within [RouteRequester.TIMEOUT_MS]. The request may still have arrived: the emulator navigation
   * shows the route search but sends no event back (verified 2026-11-12, NaviHelper service 3.0.4).
   */
  TIMED_OUT,
}

/** Hands a destination to the car's navigation. The only way the app talks to NaviHelper for routes. */
interface RouteRequester {
  suspend fun requestRoute(target: RouteTarget): RouteResult

  companion object {
    const val TIMEOUT_MS = 5_000L
  }
}
