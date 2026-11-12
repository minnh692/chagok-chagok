package com.chagokchagok.app.location

/**
 * Planned real-car source: the Pleos built-in navigation via the NaviHelper SDK.
 *
 * TODO(navihelper): implement once the emulator navigation works again. Sketch, verified earlier against
 *  `ai.pleos.playground:NaviHelper:3.0.5`:
 *  - add `implementation(libs.pleos.navihelper)` and the `pleos.car.permission.NAVI_ROUTE` permission back;
 *  - create `NaviHelper(context)`, `addListener(NaviHelperEventListener)`, `initialize(onConnected, onFailed)`;
 *  - on connect call `getCurrentLocationInfo()` and resume with the `onCurrentLocationInfo` lat/lon;
 *  - always `removeListener` + `release()` afterwards (a released instance cannot be reused).
 *  On the emulator NaviHelper returns a fixed position and ignores virtual GPS, so P0 does not use it.
 */
class NaviHelperLocationSource : LocationSource {
  override val name = "NaviHelper"

  override suspend fun readOnce(): LocationRead = LocationRead.Unavailable
}
