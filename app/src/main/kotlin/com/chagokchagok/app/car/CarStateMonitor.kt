package com.chagokchagok.app.car

import android.car.Car
import android.car.VehicleIgnitionState
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Reads AAOS car state through the platform Car API:
 * - UX restrictions (the app hides its UI while distraction optimization is required)
 * - ignition state, if the vehicle exposes it
 */
class CarStateMonitor(context: Context) {
  private val appContext = context.applicationContext
  private var car: Car? = null
  private var uxManager: CarUxRestrictionsManager? = null

  /** True while driving restrictions are active. Defaults to false when the Car service is unavailable. */
  private val _uxRestricted = MutableStateFlow(false)
  val uxRestricted: StateFlow<Boolean> = _uxRestricted

  private val _ignition = MutableStateFlow("unknown")
  val ignition: StateFlow<String> = _ignition

  fun start() {
    if (car != null) return
    val connected = runCatching { Car.createCar(appContext) }.getOrNull()
    if (connected == null) {
      _ignition.value = "unsupported (Car service unavailable)"
      return
    }
    car = connected

    uxManager = (connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager)?.also { manager ->
      _uxRestricted.value = manager.currentCarUxRestrictions?.isRequiresDistractionOptimization ?: false
      manager.registerListener { restrictions ->
        _uxRestricted.value = restrictions.isRequiresDistractionOptimization
      }
    }

    readIgnition(connected)
  }

  fun refresh() {
    car?.let(::readIgnition)
  }

  fun stop() {
    uxManager?.unregisterListener()
    uxManager = null
    car?.disconnect()
    car = null
  }

  private fun readIgnition(car: Car) {
    _ignition.value = runCatching {
      val properties = car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        ?: return@runCatching "unsupported (no CarPropertyManager)"
      if (properties.getCarPropertyConfig(VehiclePropertyIds.IGNITION_STATE) == null) {
        return@runCatching "unsupported"
      }
      ignitionName(properties.getIntProperty(VehiclePropertyIds.IGNITION_STATE, 0))
    }.getOrElse { e -> "unsupported (${e.javaClass.simpleName}: ${e.message})" }
  }

  private fun ignitionName(value: Int) = when (value) {
    VehicleIgnitionState.LOCK -> "LOCK"
    VehicleIgnitionState.OFF -> "OFF"
    VehicleIgnitionState.ACC -> "ACC"
    VehicleIgnitionState.ON -> "ON"
    VehicleIgnitionState.START -> "START"
    else -> "UNDEFINED ($value)"
  }
}
