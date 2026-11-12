package com.chagokchagok.app.region

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Loads and indexes the bundled region data once per process. */
object RegionAssets {
  const val ASSET_PATH = "regions/sgg230.geojson"

  private val mutex = Mutex()

  @Volatile private var cached: RegionIndex? = null

  suspend fun load(context: Context): RegionIndex {
    cached?.let { return it }
    return mutex.withLock {
      cached ?: withContext(Dispatchers.Default) {
        val text = context.applicationContext.assets.open(ASSET_PATH)
          .bufferedReader(Charsets.UTF_8)
          .use { it.readText() }
        RegionIndex.fromGeoJson(text)
      }.also { cached = it }
    }
  }
}
