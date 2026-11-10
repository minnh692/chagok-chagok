package com.chagokchagok.app.location

import android.content.Context
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Location from the Pleos built-in navigation via NaviHelper SDK.
 * Note: on the Pleos Connect Emulator the navigation does not follow virtual GPS.
 */
class NaviHelperLocationSource(context: Context) : LocationSource {
  private val appContext = context.applicationContext
  // A released NaviHelper cannot be reused, so a new instance is created on every start().
  private var naviHelper: NaviHelper? = null

  override val name = "NaviHelper"
  private val _status = MutableStateFlow<SourceStatus>(SourceStatus.Idle)
  override val status: StateFlow<SourceStatus> = _status
  private val _latest = MutableStateFlow<LocationFix?>(null)
  override val latest: StateFlow<LocationFix?> = _latest

  private val listener = object : NaviHelperEventListener {
    override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
      val info = currentLocationInfo
      _latest.value = LocationFix(info.latitude, info.longitude, address = info.address)
    }

    override fun onError(errorCode: NaviErrorCode) {
      _status.value = SourceStatus.Failed("onError: $errorCode")
    }
  }

  override fun start() {
    if (naviHelper != null) return
    val helper = NaviHelper(appContext)
    naviHelper = helper
    _status.value = SourceStatus.Connecting
    helper.addListener(listener)
    helper.initialize(
      onConnected = {
        _status.value = SourceStatus.Ready
        helper.getCurrentLocationInfo()
      },
      onFailed = { throwable ->
        _status.value = SourceStatus.Failed(throwable.message ?: throwable.javaClass.simpleName)
      },
    )
  }

  override fun refresh() {
    if (_status.value == SourceStatus.Ready) naviHelper?.getCurrentLocationInfo()
  }

  override fun stop() {
    val helper = naviHelper ?: return
    helper.removeListener(listener)
    helper.release()
    naviHelper = null
    _status.value = SourceStatus.Idle
  }
}
