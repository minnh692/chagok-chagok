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
    // TODO(border-correction): simplified borders can be off by tens of meters, so a point very close to
    //  a border may resolve to the neighbor (or to nothing on a coastline). Correct that here, e.g. by
    //  preferring the region already stamped today when the point is within a small tolerance of it.
    return index.regions.firstOrNull { it.contains(longitude, latitude) }
  }
}
