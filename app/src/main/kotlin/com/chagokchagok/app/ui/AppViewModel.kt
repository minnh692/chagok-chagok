package com.chagokchagok.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chagokchagok.app.collection.CoastalRegions
import com.chagokchagok.app.collection.CollectionProgress
import com.chagokchagok.app.collection.Collections
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

/** A collection that was just completed: a set ([progress]) or a two-season region ([seasonRegion]). */
data class Completion(val id: String, val progress: CollectionProgress?, val seasonRegion: Region?)

/** Top-level screens. The today screen is home; every other screen goes back to it. */
enum class Screen { CONSENT, TODAY, MY_MAP }

/** What the last parked check-in found. Shown at the top of the today screen. */
sealed interface CheckIn {
  data object Idle : CheckIn
  data object Locating : CheckIn
  data class Done(val result: StampResult, val region: Region) : CheckIn
  data object OutsideRegions : CheckIn
  data object LocationUnavailable : CheckIn
  data object PermissionNeeded : CheckIn
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
  private val consentStore = ConsentStore(app)
  private val repository = StampRepository(AppDatabase.get(app).stampDao())
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

  init {
    // Parse and index the bundled boundaries once per process.
    viewModelScope.launch {
      val index = RegionAssets.load(app)
      _regions.value = index
      _collections.value = withContext(Dispatchers.Default) { Collections(index, CoastalRegions.derive(index)) }
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

  /** Back from any screen returns to the today screen. Returns false when already there. */
  fun back(): Boolean {
    if (_screen.value == Screen.TODAY) return false
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
    return wasRestricted && !restricted
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
    if (!hasPermission) {
      _checkIn.value = CheckIn.PermissionNeeded
      return
    }
    if (checkInJob?.isActive == true) return
    checkInJob = viewModelScope.launch {
      _checkIn.value = CheckIn.Locating
      val regions = RegionAssets.load(getApplication())
      _checkIn.value = when (val read = locationSource.readOnce()) {
        LocationRead.PermissionMissing -> CheckIn.PermissionNeeded
        LocationRead.Unavailable -> CheckIn.LocationUnavailable
        is LocationRead.Fix -> {
          val region = withContext(Dispatchers.Default) {
            RegionFinder(regions).find(read.fix.latitude, read.fix.longitude)
          }
          if (region == null) {
            CheckIn.OutsideRegions
          } else {
            CheckIn.Done(repository.stamp(region.code, currentDay()), region)
          }
        }
      }
    }
  }

  private companion object {
    const val PREFS = "settings"
    const val KEY_NAVI_DISMISSED = "navi_prompt_dismissed_on"
  }
}
