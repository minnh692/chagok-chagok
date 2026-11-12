package com.chagokchagok.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chagokchagok.app.collection.CoastalRegions
import com.chagokchagok.app.collection.CollectionProgress
import com.chagokchagok.app.collection.Collections
import com.chagokchagok.app.collection.NextStamp
import com.chagokchagok.app.collection.NextStamps
import com.chagokchagok.app.navi.NaviHelperRouteRequester
import com.chagokchagok.app.navi.MissedStamp
import com.chagokchagok.app.navi.MissedStampFinder
import com.chagokchagok.app.navi.NaviHelperRecentDestinations
import com.chagokchagok.app.navi.RecentDestinationSource
import com.chagokchagok.app.data.RestVisitEntity
import com.chagokchagok.app.restarea.RestArea
import com.chagokchagok.app.restarea.RestAreaCatalog
import com.chagokchagok.app.restarea.RestAreaFinder
import com.chagokchagok.app.navi.RouteRequester
import com.chagokchagok.app.navi.RouteResult
import com.chagokchagok.app.navi.RouteTarget
import com.chagokchagok.app.consent.ConsentStore
import com.chagokchagok.app.consent.LocationConsent
import com.chagokchagok.app.data.AppDatabase
import com.chagokchagok.app.data.CompanionType
import com.chagokchagok.app.data.StampBook
import com.chagokchagok.app.data.StampRepository
import com.chagokchagok.app.data.StampResult
import com.chagokchagok.app.location.AndroidLocationSource
import com.chagokchagok.app.location.LocationRead
import com.chagokchagok.app.location.LocationSource
import com.chagokchagok.app.region.Region
import com.chagokchagok.app.region.RegionAssets
import com.chagokchagok.app.region.RegionFinder
import com.chagokchagok.app.region.RegionIndex
import com.chagokchagok.app.ui.map.Arrival
import com.chagokchagok.app.ui.map.MapFilter
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Recommendations and the region they start from (null when nothing is stamped yet). */
data class Recommendations(val from: Region?, val items: List<NextStamp>)

/** A collection that was just completed: a set ([progress]) or a two-season region ([seasonRegion]). */
data class Completion(val id: String, val progress: CollectionProgress?, val seasonRegion: Region?)

/** Top-level screens. The today screen is home; every other screen goes back to it. */
enum class Screen { CONSENT, TODAY, MY_MAP, NEXT }

/** Handing a recommended region to the car's navigation (C3). */
sealed interface Handoff {
  data object Idle : Handoff
  data object Requesting : Handoff
  /** The navigation took the destination. */
  data object Started : Handoff
  /** The destination went to the navigation, which shows its route search; the navigation was brought forward. */
  data object Sent : Handoff
  /** NaviHelper failed or could not connect; the navigation app was opened for manual entry. */
  data object Manual : Handoff
}

/** What the last parked check-in found. Shown at the top of the today screen. */
sealed interface CheckIn {
  data object Idle : CheckIn
  data object Locating : CheckIn
  data class Done(val result: StampResult, val region: Region) : CheckIn

  /**
   * Parked within 300 m of an expressway rest area: the rest area is collected instead of stamping the region.
   * [firstTime]: never collected before; [alreadyToday]: already collected today (nothing new stored).
   */
  data class RestStop(val area: RestArea, val firstTime: Boolean, val alreadyToday: Boolean, val collected: Int, val total: Int) : CheckIn
  data object OutsideRegions : CheckIn
  data object LocationUnavailable : CheckIn
  data object PermissionNeeded : CheckIn
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
  private val consentStore = ConsentStore(app)
  private val repository = StampRepository(AppDatabase.get(app).stampDao())
  private val restVisits = AppDatabase.get(app).restVisitDao()
  // The single place that decides where location comes from (see LocationSource).
  private val locationSource: LocationSource = AndroidLocationSource(app)

  val consent: StateFlow<LocationConsent> = consentStore.consent

  private val _screen = MutableStateFlow(
    if (consentStore.consent.value == LocationConsent.UNASKED) Screen.CONSENT else Screen.TODAY,
  )
  val screen: StateFlow<Screen> = _screen

  private val _regions = MutableStateFlow<RegionIndex?>(null)
  val regions: StateFlow<RegionIndex?> = _regions

  private val _checkIn = MutableStateFlow<CheckIn>(CheckIn.Idle)
  val checkIn: StateFlow<CheckIn> = _checkIn

  // Blocked until the car service reports otherwise: the app is distraction optimized, so it must never
  // show its normal UI before it knows the car is parked.
  private val _drivingRestricted = MutableStateFlow(true)
  val drivingRestricted: StateFlow<Boolean> = _drivingRestricted

  /** Debug builds can shift "today" to test day changes on the emulator (see MainActivity). */
  var dayOffset: Long = 0
    set(value) {
      field = value
      _today.value = currentDay()
    }
  private val _today = MutableStateFlow(currentDay())

  /**
   * The companion just tapped, applied on top of stored stamps so the chip and the map change in the same
   * frame as the tap instead of after the database round trip. Keyed by date; it matches what gets stored.
   */
  private val companionChoice = MutableStateFlow<Pair<String, CompanionType?>?>(null)

  val book: StateFlow<StampBook> =
    combine(repository.observeStamps(), _today, companionChoice) { stamps, day, choice ->
      val today = day.toString()
      val shown = if (choice == null || choice.first != today) {
        stamps
      } else {
        stamps.map { if (it.date == today) it.copy(companion = choice.second?.key) else it }
      }
      StampBook(shown, today)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StampBook.empty(_today.value.toString()))

  private val _filter = MutableStateFlow(MapFilter.MINE)
  val filter: StateFlow<MapFilter> = _filter

  private var checkInJob: Job? = null

  // --- Collections ----------------------------------------------------------------------------
  private val _collections = MutableStateFlow<Collections?>(null)
  val collections: StateFlow<Collections?> = _collections

  /** Collections completed while the app is open, newest last; shown as cards until dismissed. Not stored. */
  private val _completions = MutableStateFlow<List<Completion>>(emptyList())
  val completions: StateFlow<List<Completion>> = _completions

  /**
   * Compares completed collections between stored-stamp updates. The first reading after launch is the baseline,
   * so only collections completed from now on (a new stamp, a companion choice) are announced.
   */
  private fun watchCompletions() {
    viewModelScope.launch {
      // The stamps as stored when the app opened. Collections may finish loading after a check-in already
      // stamped, so the baseline is computed from this snapshot, not from whatever is stored by then.
      var opening: List<com.chagokchagok.app.data.StampEntity>? = null
      var baseline: Set<String>? = null
      combine(repository.observeStamps(), _today, _collections) { stamps, day, c -> Triple(stamps, day, c) }
        .collect { (stamps, day, c) ->
          if (opening == null) opening = stamps
          if (c == null) return@collect
          val book = StampBook(stamps, day.toString())
          val done = withContext(Dispatchers.Default) { c.completedIds(book) }
          val previous = baseline ?: withContext(Dispatchers.Default) {
            c.completedIds(StampBook(opening.orEmpty(), day.toString()))
          }
          baseline = done
          val fresh = (done - previous).sorted()
          if (fresh.isEmpty()) return@collect
          val byId = c.progress(book).associateBy { it.id }
          _completions.value = _completions.value + fresh.map { id ->
            val code = id.removePrefix("${Collections.SEASON_ID}:")
            Completion(id, byId[id], if (id.startsWith("${Collections.SEASON_ID}:")) _regions.value?.get(code) else null)
          }
        }
    }
  }

  fun dismissCompletion(id: String) {
    _completions.value = _completions.value.filterNot { it.id == id }
  }

  // --- Navigation prompt ------------------------------------------------------------------------
  // Only the date the prompt was dismissed is kept (no place, no time of day).
  private val prefs = app.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
  private val naviDismissedOn = MutableStateFlow(prefs.getString(KEY_NAVI_DISMISSED, null))

  /** Whether the car has a navigation app to open; set by the activity. */
  val naviAvailable = MutableStateFlow(false)

  /**
   * "다음 도장은 내비로 찾아가요": shown while parked once today's check-in has finished, until it is
   * dismissed or used today.
   */
  val naviPrompt: StateFlow<Boolean> =
    combine(consent, _checkIn, _today, naviDismissedOn, naviAvailable) { consent, checkIn, today, dismissed, available ->
      available && consent == LocationConsent.GRANTED && checkIn is CheckIn.Done && dismissed != today.toString()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  /** Hides the navigation prompt for the rest of today (called on both of its buttons). */
  fun dismissNaviPrompt() {
    val day = currentDay().toString()
    prefs.edit().putString(KEY_NAVI_DISMISSED, day).apply()
    naviDismissedOn.value = day
  }

  // --- Next stamp (C2 / C3) ------------------------------------------------------------------------
  private val routeRequester: RouteRequester = NaviHelperRouteRequester(app)

  private val _nextStamps = MutableStateFlow<NextStamps?>(null)

  /** Up to 3 nearest unstamped regions from today's check-in region, or from the latest stamp. */
  val recommendations: StateFlow<Recommendations?> =
    combine(book, _nextStamps, _checkIn) { book, next, checkIn ->
      next ?: return@combine null
      val from = (checkIn as? CheckIn.Done)?.region?.code ?: next.anchor(book) ?: return@combine Recommendations(null, emptyList())
      Recommendations(_regions.value?.get(from), withContext(Dispatchers.Default) { next.recommend(from, book) })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  /** The recommended region whose confirm screen (C3) is open. */
  private val _confirming = MutableStateFlow<String?>(null)
  val confirming: StateFlow<String?> = _confirming

  private val _handoff = MutableStateFlow<Handoff>(Handoff.Idle)
  val handoff: StateFlow<Handoff> = _handoff

  fun confirmNext(code: String?) {
    if (_handoff.value == Handoff.Requesting) return
    _confirming.value = code
    _handoff.value = Handoff.Idle
  }

  /**
   * "내비로 안내" / "다시 보내기": opens the confirm for [code] and remembers the destination region (code only) and asks the navigation for a route to the
   * region's label point. On error or after [RouteRequester.TIMEOUT_MS], [openNavigationApp] is called instead.
   */
  fun startRoute(code: String, openNavigationApp: () -> Unit) {
    val region = _regions.value?.get(code) ?: return
    if (_handoff.value == Handoff.Requesting || _drivingRestricted.value) return
    prefs.edit().putString(KEY_NEXT_DESTINATION, code).apply()
    _confirming.value = code
    _handoff.value = Handoff.Requesting
    viewModelScope.launch {
      val result = routeRequester.requestRoute(RouteTarget(region.name, region.fullName, region.labelLat, region.labelLon))
      _handoff.value = when (result) {
        RouteResult.STARTED -> Handoff.Started
        RouteResult.SENT -> Handoff.Sent.also { openNavigationApp() }
        RouteResult.FAILED, RouteResult.TIMED_OUT -> Handoff.Manual.also { openNavigationApp() }
      }
    }
  }

  /** The region chosen with "내비로 안내", or null. Only the region code is stored. */
  val nextDestination: String? get() = prefs.getString(KEY_NEXT_DESTINATION, null)

  // --- Rest areas ---------------------------------------------------------------------------------
  private val _restAreas = MutableStateFlow<List<RestArea>?>(null)

  /** "고속도로 휴게소" progress: distinct rest areas collected (both directions count as one). */
  val restProgress: StateFlow<CollectionProgress?> =
    combine(restVisits.observeAll(), _restAreas) { visits, areas ->
      areas ?: return@combine null
      val done = visits.mapTo(HashSet()) { it.restAreaId }.count { id -> areas.any { it.id == id } }
      CollectionProgress(REST_ID, CollectionProgress.Kind.REST, "고속도로 휴게소", done, areas.size)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  /** Stores today's visit to [area] (id + date only) and reports it. */
  private suspend fun collectRestArea(area: RestArea, areas: List<RestArea>): CheckIn.RestStop {
    val day = currentDay().toString()
    val before = restVisits.visitsTo(area.id)
    val alreadyToday = before.any { it.date == day }
    if (!alreadyToday) restVisits.insert(RestVisitEntity(restAreaId = area.id, date = day))
    val collected = restProgress.value?.done?.plus(if (before.isEmpty()) 1 else 0) ?: 1
    return CheckIn.RestStop(area, firstTime = before.isEmpty(), alreadyToday = alreadyToday, collected = collected, total = areas.size)
  }

  // --- Navigation history fill-in (J-1) --------------------------------------------------------------
  private val recentSource: RecentDestinationSource = NaviHelperRecentDestinations(app)
  private var historyChecked = false

  private val _missed = MutableStateFlow<List<MissedStamp>>(emptyList())

  /** "빠진 도장": unstamped regions from the navigation history, until stamped or removed. Not stored. */
  val missed: StateFlow<List<MissedStamp>> = _missed

  /** Random per-install salt for the destination hashes; generated once and kept with the hashes. */
  private fun salt(): ByteArray {
    prefs.getString(KEY_NAVI_SALT, null)?.let { return android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }
    val bytes = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
    prefs.edit().putString(KEY_NAVI_SALT, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)).apply()
    return bytes
  }

  private fun markSeen(hashes: Set<String>) {
    if (hashes.isEmpty()) return
    val seen = prefs.getStringSet(KEY_NAVI_SEEN, emptySet()).orEmpty()
    prefs.edit().putStringSet(KEY_NAVI_SEEN, seen + hashes).apply()
  }

  /** Once per app open, while parked and with consent: compares the navigation history with the stamps. */
  private fun checkNaviHistory() {
    if (historyChecked) return
    historyChecked = true
    viewModelScope.launch {
      val history = recentSource.recentDestinations() ?: return@launch // unavailable or no answer in 5 s
      val regions = RegionAssets.load(getApplication())
      val finder = MissedStampFinder(salt()) { lat, lon -> RegionFinder(regions).find(lat, lon)?.code }
      val seen = prefs.getStringSet(KEY_NAVI_SEEN, null)
      val result = withContext(Dispatchers.Default) { finder.process(history, seen, book.value.stampedCodes) }
      // The first run stores an empty set too, so later runs know the baseline was taken.
      prefs.edit().putStringSet(KEY_NAVI_SEEN, seen.orEmpty() + result.markSeen).apply()
      _missed.value = result.suggestions
    }
  }

  fun stampMissed(item: MissedStamp, date: LocalDate) {
    if (_drivingRestricted.value || date.isAfter(currentDay())) return
    _missed.value = _missed.value - item
    markSeen(item.hashes)
    viewModelScope.launch { repository.stamp(item.regionCode, date) }
  }

  fun dismissMissed(item: MissedStamp) {
    _missed.value = _missed.value - item
    markSeen(item.hashes)
  }

  init {
    viewModelScope.launch { _restAreas.value = RestAreaCatalog.load(app) }
    // Parse and index the bundled boundaries once per process.
    viewModelScope.launch {
      val index = RegionAssets.load(app)
      _regions.value = index
      val collections = withContext(Dispatchers.Default) { Collections(index, CoastalRegions.derive(index)) }
      _collections.value = collections
      _nextStamps.value = withContext(Dispatchers.Default) { NextStamps(index, collections) }
    }
    watchCompletions()
  }

  private fun currentDay(): LocalDate = LocalDate.now().plusDays(dayOffset)

  // --- Navigation -------------------------------------------------------------------------------

  fun openConsent() {
    _screen.value = Screen.CONSENT
  }

  fun openMyMap() {
    _screen.value = Screen.MY_MAP
  }

  /** Where back from the recommendation screen goes. */
  private var nextReturnsTo = Screen.TODAY

  fun openNext() {
    if (_screen.value != Screen.NEXT) nextReturnsTo = _screen.value
    _confirming.value = null
    _handoff.value = Handoff.Idle
    _screen.value = Screen.NEXT
  }

  /** Back from any screen returns to the today screen. Returns false when already there. */
  fun back(): Boolean {
    if (_screen.value == Screen.TODAY) return false
    if (_screen.value == Screen.NEXT) {
      if (_confirming.value != null && _handoff.value != Handoff.Requesting) {
        _confirming.value = null
        _handoff.value = Handoff.Idle
        return true
      }
      _screen.value = nextReturnsTo
      return true
    }
    if (_screen.value == Screen.CONSENT && consentStore.consent.value == LocationConsent.UNASKED) {
      consentStore.set(LocationConsent.DECLINED)
    }
    _screen.value = Screen.TODAY
    return true
  }

  // --- Consent ----------------------------------------------------------------------------------

  fun acceptConsent() {
    consentStore.set(LocationConsent.GRANTED)
    _screen.value = Screen.TODAY
  }

  fun declineConsent() {
    consentStore.set(LocationConsent.DECLINED)
    _checkIn.value = CheckIn.Idle
    _screen.value = Screen.TODAY
  }

  // --- Today ----------------------------------------------------------------------------------

  /** Saves [companion] on today's stamps; null (tapping the chosen chip again) clears it. */
  fun setTodayCompanion(companion: CompanionType?) {
    val day = currentDay()
    companionChoice.value = day.toString() to companion
    viewModelScope.launch { repository.setCompanion(day, companion) }
  }

  fun saveMemo(stampId: Long, memo: String) {
    viewModelScope.launch { repository.setMemo(stampId, memo) }
  }

  fun setFilter(filter: MapFilter) {
    _filter.value = filter
  }

  // --- Car state --------------------------------------------------------------------------------

  /** Returns true when restrictions just cleared (the car was parked while the app is open). */
  fun setDrivingRestricted(restricted: Boolean): Boolean {
    val wasRestricted = _drivingRestricted.value
    _drivingRestricted.value = restricted
    if (restricted) {
      // Nothing animates while driving, and an interrupted arrival is not replayed later.
      _arrival.value = null
      arrivalArmed = false
    } else if (wasRestricted) {
      arrivalArmed = true
    }
    return wasRestricted && !restricted
  }

  // --- Arrival animation ------------------------------------------------------------------------
  /** Set when restrictions clear; the next check-in that stamps a new region plays the arrival once. */
  private var arrivalArmed = false

  private val _arrival = MutableStateFlow<Arrival?>(null)
  val arrival: StateFlow<Arrival?> = _arrival

  fun arrivalShown() {
    _arrival.value = null
  }

  // --- Check-in ---------------------------------------------------------------------------------

  /**
   * Reads the location once and stamps the region. Called when the app opens or resumes while parked.
   * Does nothing (and reads nothing) without consent or while driving restrictions are active.
   */
  fun checkIn(hasPermission: Boolean) {
    _today.value = currentDay()
    if (consentStore.consent.value != LocationConsent.GRANTED) return
    if (_drivingRestricted.value) return
    checkNaviHistory()
    if (!hasPermission) {
      _checkIn.value = CheckIn.PermissionNeeded
      return
    }
    if (checkInJob?.isActive == true) return
    val animate = arrivalArmed
    arrivalArmed = false
    checkInJob = viewModelScope.launch {
      _checkIn.value = CheckIn.Locating
      val regions = RegionAssets.load(getApplication())
      _checkIn.value = when (val read = locationSource.readOnce()) {
        LocationRead.PermissionMissing -> CheckIn.PermissionNeeded
        LocationRead.Unavailable -> CheckIn.LocationUnavailable
        is LocationRead.Fix -> {
          // A rest area takes priority: it is collected and the region is not stamped (the car only passed through).
          val areas = RestAreaCatalog.load(getApplication())
          val restArea = withContext(Dispatchers.Default) { RestAreaFinder(areas).find(read.fix.latitude, read.fix.longitude) }
          if (restArea != null) {
            _checkIn.value = collectRestArea(restArea, areas)
            return@launch
          }
          val region = withContext(Dispatchers.Default) {
            RegionFinder(regions).find(read.fix.latitude, read.fix.longitude)
          }
          if (region == null) {
            CheckIn.OutsideRegions
          } else {
            // The region stamped just before this one, for the arrival line (read before stamping).
            val today = currentDay().toString()
            val previous = book.value.stamps.filter { it.regionCode != region.code && it.date <= today }
              .maxWithOrNull(compareBy({ it.date }, { it.dayOrder }))?.regionCode
            val result = repository.stamp(region.code, currentDay())
            if (result is StampResult.New) {
              if (nextDestination == region.code) prefs.edit().remove(KEY_NEXT_DESTINATION).apply()
              // Only first stamps animate, and only right after parking; never while restricted.
              if (animate && !_drivingRestricted.value) _arrival.value = Arrival(previous, region.code)
            }
            CheckIn.Done(result, region)
          }
        }
      }
    }
  }

  private companion object {
    const val PREFS = "settings"
    const val KEY_NAVI_DISMISSED = "navi_prompt_dismissed_on"
    const val KEY_NEXT_DESTINATION = "next_destination_region"
    const val REST_ID = "rest"
    const val KEY_NAVI_SALT = "navi_history_salt"
    const val KEY_NAVI_SEEN = "navi_history_seen"
  }
}
