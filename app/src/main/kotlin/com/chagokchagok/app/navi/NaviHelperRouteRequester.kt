package com.chagokchagok.app.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
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
 * Asks the Pleos navigation for a route with NaviHelper (`requestRoute`, permission NAVI_ROUTE_SEARCH).
 * A fresh NaviHelper is created per request and always released afterwards (a released instance cannot be reused).
 */
class NaviHelperRouteRequester(context: Context) : RouteRequester {
  private val app = context.applicationContext

  @SuppressLint("MissingPermission") // Declared in the manifest; a denial surfaces as an error / timeout.
  override suspend fun requestRoute(target: RouteTarget): RouteResult = withContext(Dispatchers.Main) {
    val answer = CompletableDeferred<RouteResult>()
    val helper = NaviHelper(app)
    val listener = object : NaviHelperEventListener {
      override fun onRoutingCompleted() { answer.complete(RouteResult.STARTED) }
      override fun onRouteStarted(info: RouteStartInfo) { answer.complete(RouteResult.STARTED) }
      override fun onError(code: NaviErrorCode) {
        Log.w(TAG, "navigation error $code")
        answer.complete(RouteResult.FAILED)
      }
    }
    try {
      helper.addListener(listener)
      helper.initialize(
        {
          runCatching {
            helper.requestRoute(
              RouteInfo(
                latitude = target.latitude,
                longitude = target.longitude,
                displayLatitude = null,
                displayLongitude = null,
                poiName = target.name,
                // No POI: the navigation routes to the coordinate itself.
                poiId = "",
                address = target.address,
                poiSubId = "",
                routeOption = RouteOption.RECOMMENDED,
                isMobileRequest = false,
              ),
            )
          }.onFailure {
            Log.w(TAG, "requestRoute failed", it)
            answer.complete(RouteResult.FAILED)
          }
        },
        { error ->
          Log.w(TAG, "NaviHelper connection failed", error)
          answer.complete(RouteResult.FAILED)
        },
      )
      withTimeoutOrNull(RouteRequester.TIMEOUT_MS) { answer.await() } ?: RouteResult.TIMED_OUT
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) throw e
      Log.w(TAG, "NaviHelper unavailable", e)
      RouteResult.FAILED
    } finally {
      runCatching { helper.removeListener(listener) }
      runCatching { helper.release() }
    }
  }

  private companion object {
    const val TAG = "NaviRoute"
  }
}
