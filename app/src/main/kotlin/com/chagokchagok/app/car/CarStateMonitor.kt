package com.chagokchagok.app.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Watches AAOS driving restrictions through CarUxRestrictionsManager. The app does not declare
 * distraction optimization, so it replaces its whole UI with a static notice while restrictions are active.
 *
 * Parked detection in P0 is "no driving restrictions". TODO(parked): also check the gear with the Pleos
 * Vehicle SDK (`Vehicle.getGear()`), since IGNITION_STATE is not exposed on Pleos Connect.
 */
class CarStateMonitor(context: Context) {
  private val appContext = context.applicationContext
  private var car: Car? = null
  private var uxManager: CarUxRestrictionsManager? = null

  /** True while driving restrictions are active. False when the Car service is unavailable. */
  private val _uxRestricted = MutableStateFlow(false)
  val uxRestricted: StateFlow<Boolean> = _uxRestricted

  fun start() {
    if (car != null) return
    val connected = runCatching { Car.createCar(appContext) }.getOrNull() ?: return
    car = connected
    uxManager = (connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager)?.also { manager ->
      _uxRestricted.value = manager.currentCarUxRestrictions?.isRequiresDistractionOptimization ?: false
      manager.registerListener { restrictions ->
        _uxRestricted.value = restrictions.isRequiresDistractionOptimization
      }
    }
  }

  fun stop() {
    uxManager?.unregisterListener()
    uxManager = null
    car?.disconnect()
    car = null
  }
}
