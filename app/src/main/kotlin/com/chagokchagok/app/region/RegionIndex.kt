package com.chagokchagok.app.region

/** All regions, loaded once. Lookup by code for stored stamps, in data order for drawing. */
class RegionIndex(val regions: List<Region>) {
  private val byCode: Map<String, Region> = regions.associateBy { it.code }

  val size: Int get() = regions.size

  init {
    require(byCode.size == regions.size) { "duplicate region codes" }
  }

  operator fun get(code: String): Region? = byCode[code]

  companion object {
    fun fromGeoJson(geoJson: String) = RegionIndex(GeoJsonRegionParser.parse(geoJson))
  }
}

/** Finds the region that contains a point: bounding boxes first, then the polygon test. */
class RegionFinder(private val index: RegionIndex) {
  /** Returns null when the point is outside every region (sea, abroad). */
  fun find(latitude: Double, longitude: Double): Region? {
    // Plain polygon test. Check-ins go through BorderCorrection, which revisits points near a border.
    return index.regions.firstOrNull { it.contains(longitude, latitude) }
  }
}
