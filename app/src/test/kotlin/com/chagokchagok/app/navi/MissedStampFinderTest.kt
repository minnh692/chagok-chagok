package com.chagokchagok.app.navi

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A fake navigation history, standing in for NaviHelper. */
private class FakeNavi(var history: List<RecentDestination>?) : RecentDestinationSource {
  override suspend fun recentDestinations() = history
}

class MissedStampFinderTest {
  private val gyeongju = RecentDestination("p1", "불국사", 35.79, 129.33)
  private val gyeongju2 = RecentDestination("p2", "첨성대", 35.83, 129.22)
  private val seoul = RecentDestination("p3", "서울역", 37.55, 126.97)
  private val sea = RecentDestination("p4", "바다 위", 34.0, 128.0)

  // Region lookup by latitude band, in place of RegionFinder.
  private val finder = MissedStampFinder("salt".toByteArray()) { lat, _ ->
    when {
      lat in 35.7..35.9 -> "47130"
      lat in 37.5..37.6 -> "11140"
      else -> null
    }
  }

  @Test fun firstRunMarksEverythingSeenAndSuggestsNothing() = runBlocking {
    val navi = FakeNavi(listOf(gyeongju, seoul))
    val r = finder.process(navi.recentDestinations()!!, seen = null, stampedCodes = emptySet())
    assertTrue(r.suggestions.isEmpty())
    assertEquals(setOf(finder.hash(gyeongju), finder.hash(seoul)), r.markSeen)
  }

  @Test fun newDestinationsInUnstampedRegionsAreSuggestedOncePerRegion() = runBlocking {
    val navi = FakeNavi(listOf(gyeongju, gyeongju2, seoul, sea))
    val seen = setOf(finder.hash(seoul))
    val r = finder.process(navi.recentDestinations()!!, seen, stampedCodes = emptySet())
    // Two destinations in 경주 -> one suggestion carrying both hashes; 서울역 was seen; the sea point maps nowhere.
    assertEquals(listOf("47130"), r.suggestions.map { it.regionCode })
    assertEquals(setOf(finder.hash(gyeongju), finder.hash(gyeongju2)), r.suggestions.single().hashes)
    assertEquals(setOf(finder.hash(sea)), r.markSeen)
  }

  @Test fun destinationsInStampedRegionsAreMarkedSeenWithoutSuggestion() {
    val r = finder.process(listOf(gyeongju, seoul), seen = emptySet(), stampedCodes = setOf("47130", "11140"))
    assertTrue(r.suggestions.isEmpty())
    assertEquals(2, r.markSeen.size)
  }

  @Test fun hashIsSaltedAndDoesNotContainThePlace() {
    val h = finder.hash(gyeongju)
    assertEquals(64, h.length)
    assertFalse(h.contains("불국사") || h.contains("p1"))
    assertNotEquals(h, MissedStampFinder("other".toByteArray()) { _, _ -> null }.hash(gyeongju))
    assertEquals(h, finder.hash(gyeongju.copy(latitude = 0.0, longitude = 0.0)))
  }

  @Test fun unavailableNavigationGivesNoHistory() = runBlocking {
    assertEquals(null, FakeNavi(null).recentDestinations())
  }
}
