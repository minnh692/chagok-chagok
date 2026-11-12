package com.chagokchagok.app.navi

import ai.pleos.playground.navi.data.RecentDestinationInfo
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
 * Reads the navigation's recent destinations with NaviHelper `getRecentDestinationInfo` (permission NAVI_ROUTE),
 * waiting at most [TIMEOUT_MS]. A fresh NaviHelper is created and released per read.
 */
class NaviHelperRecentDestinations(context: Context) : RecentDestinationSource {
  private val app = context.applicationContext

  @SuppressLint("MissingPermission") // Declared in the manifest; a denial surfaces as a timeout.
  override suspend fun recentDestinations(): List<RecentDestination>? = withContext(Dispatchers.Main) {
    val answer = CompletableDeferred<List<RecentDestination>?>()
    val helper = NaviHelper(app)
    val listener = object : NaviHelperEventListener {
      override fun onRecentDestinationInfo(list: List<RecentDestinationInfo>) {
        answer.complete(list.map { RecentDestination(it.poiId, it.name, it.latitude, it.longitude) })
      }
    }
    try {
      helper.addListener(listener)
      helper.initialize(
        { runCatching { helper.getRecentDestinationInfo() }.onFailure { answer.complete(null) } },
        { error ->
          Log.w(TAG, "NaviHelper connection failed", error)
          answer.complete(null)
        },
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
    const val TAG = "NaviRecent"
    const val TIMEOUT_MS = 5_000L
  }
}
