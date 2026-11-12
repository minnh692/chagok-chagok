package com.chagokchagok.app.location

import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The navigation's address for the current position ("<gu> <dong>", the si can be missing). Asked only when a
 * point is near a region border (see `region/BorderCorrection`); used once in memory and never stored.
 */
interface AddressHintSource {
  /** Null when the navigation is unavailable or does not answer in time. */
  suspend fun currentAddress(): String?
}

/**
 * NaviHelper `getCurrentLocationInfo()`. On the emulator it always answers with the navigation's own fixed
 * position ("수정구 시흥동"), which then simply names none of the nearby regions.
 */
class NaviHelperAddressHintSource(context: Context) : AddressHintSource {
  private val app = context.applicationContext

  @SuppressLint("MissingPermission")
  override suspend fun currentAddress(): String? = withContext(Dispatchers.Main) {
    val answer = CompletableDeferred<String?>()
    val helper = NaviHelper(app)
    val listener = object : NaviHelperEventListener {
      override fun onCurrentLocationInfo(info: CurrentLocationInfo) { answer.complete(info.address) }
    }
    try {
      helper.addListener(listener)
      helper.initialize(
        { runCatching { helper.getCurrentLocationInfo() }.onFailure { answer.complete(null) } },
        { answer.complete(null) },
      )
      withTimeoutOrNull(TIMEOUT_MS) { answer.await() }
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) throw e
      Log.w(TAG, "NaviHelper unavailable", e)
      null
    } finally {
      runCatching { helper.removeListener(listener) }
      runCatching { helper.release() }
    }
  }

  private companion object {
    const val TAG = "NaviAddress"
    const val TIMEOUT_MS = 3_000L
  }
}
