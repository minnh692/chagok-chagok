package com.chagokchagok.app.consent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The user's answer on the in-app location consent screen (separate from the Android permission). */
enum class LocationConsent { UNASKED, GRANTED, DECLINED }

/** Keeps only the consent answer. No location data is stored here. */
class ConsentStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private val _consent = MutableStateFlow(read())
  val consent: StateFlow<LocationConsent> = _consent

  fun set(value: LocationConsent) {
    prefs.edit().putString(KEY, value.name).apply()
    _consent.value = value
  }

  private fun read(): LocationConsent =
    prefs.getString(KEY, null)?.let { runCatching { LocationConsent.valueOf(it) }.getOrNull() }
      ?: LocationConsent.UNASKED

  private companion object {
    const val PREFS = "settings"
    const val KEY = "location_consent"
  }
}
