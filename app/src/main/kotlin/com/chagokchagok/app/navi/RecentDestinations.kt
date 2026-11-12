package com.chagokchagok.app.navi

import java.security.MessageDigest

/** A destination from the navigation's history. Held in memory only; coordinates are never stored. */
data class RecentDestination(val poiId: String, val name: String, val latitude: Double, val longitude: Double)

/** The navigation's recent destinations, or null when it could not be read in time. */
interface RecentDestinationSource {
  suspend fun recentDestinations(): List<RecentDestination>?
}

/** "빠진 도장": an unstamped region the navigation history says the car went to. */
data class MissedStamp(val regionCode: String, val hashes: Set<String>)

/**
 * Turns the navigation history into stamp suggestions.
 *
 * Only a salted SHA-256 of poiId + name is remembered for each destination already handled ("seen"), so the same
 * history entry is never suggested twice and nothing readable about the place is stored.
 * - First run (no seen hashes stored yet): everything currently in the history is marked seen, nothing is suggested.
 * - Later: each unseen destination is mapped to a region (in memory). Destinations in already-stamped regions or
 *   outside every region are marked seen right away; the rest become one suggestion per region, marked seen only
 *   when the user stamps or removes it.
 */
class MissedStampFinder(private val salt: ByteArray, private val regionAt: (lat: Double, lon: Double) -> String?) {

  data class Result(val markSeen: Set<String>, val suggestions: List<MissedStamp>)

  fun hash(d: RecentDestination): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(salt)
    digest.update("${d.poiId}\u0000${d.name}".toByteArray(Charsets.UTF_8))
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  fun process(history: List<RecentDestination>, seen: Set<String>?, stampedCodes: Set<String>): Result {
    val hashed = history.map { hash(it) to it }
    if (seen == null) return Result(hashed.mapTo(LinkedHashSet()) { it.first }, emptyList())
    val markSeen = LinkedHashSet<String>()
    val byRegion = LinkedHashMap<String, MutableSet<String>>()
    for ((h, d) in hashed) {
      if (h in seen) continue
      val code = regionAt(d.latitude, d.longitude)
      if (code == null || code in stampedCodes) markSeen += h else byRegion.getOrPut(code) { LinkedHashSet() } += h
    }
    return Result(markSeen, byRegion.map { (code, hashes) -> MissedStamp(code, hashes) })
  }
}
