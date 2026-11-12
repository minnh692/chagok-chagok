package com.chagokchagok.app.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Watches AAOS driving restrictions through CarUxRestrictionsManager. The activity is declared distraction
 * optimized, so the system does not hide it; the app itself replaces its whole UI with a static notice
 * while restrictions are active.
 *
 * Parked detection in P0 is "no driving restrictions". TODO(parked): also check the gear with the Pleos
 * Vehicle SDK (`Vehicle.getGear()`), since IGNITION_STATE is not exposed on Pleos Connect.
 */
class CarStateMonitor(context: Context) {
  private val appContext = context.applicationContext
  private var car: Car? = null
  private var uxManager: CarUxRestrictionsManager? = null

  /**
   * True while driving restrictions are active. Starts true (blocked) until the first reading;
   * false when the device has no Car service (not a car), so the app stays usable there.
   */
  private val _uxRestricted = MutableStateFlow(true)
  val uxRestricted: StateFlow<Boolean> = _uxRestricted

  fun start() {
    if (car != null) return
    val connected = runCatching { Car.createCar(appContext) }.getOrNull()
    if (connected == null) {
      publish(false)
      return
    }
    car = connected
    val manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
    if (manager == null) publish(false)
    uxManager = manager?.also { manager ->
      publish(manager.currentCarUxRestrictions?.isRequiresDistractionOptimization ?: false)
      manager.registerListener { restrictions -> publish(restrictions.isRequiresDistractionOptimization) }
    }
  }

  private fun publish(restricted: Boolean) {
    if (_uxRestricted.value != restricted) Log.i(TAG, "driving restrictions active=$restricted")
    _uxRestricted.value = restricted
  }

  fun stop() {
    uxManager?.unregisterListener()
    uxManager = null
    car?.disconnect()
    car = null
  }

  private companion object {
    const val TAG = "ChagokCar"
  }
}
