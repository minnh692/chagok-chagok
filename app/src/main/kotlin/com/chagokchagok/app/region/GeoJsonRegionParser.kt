package com.chagokchagok.app.region

/** Parses the bundled region FeatureCollection (Polygon / MultiPolygon features) into [Region]s. */
object GeoJsonRegionParser {
  fun parse(geoJson: String): List<Region> {
    val json = JsonCursor(geoJson)
    val regions = ArrayList<Region>(256)
    json.readObject { key ->
      if (key == "features") json.readArray { regions += readFeature(json) } else json.skipValue()
    }
    return regions
  }

  private fun readFeature(json: JsonCursor): Region {
    var code: String? = null
    var sido: String? = null
    var name: String? = null
    var labelLon = Double.NaN
    var labelLat = Double.NaN
    var polygons: List<RegionPolygon> = emptyList()
    json.readObject { key ->
      when (key) {
        "properties" -> json.readObject { property ->
          when (property) {
            "code" -> code = json.readString()
            "sido" -> sido = json.readString()
            "name" -> name = json.readString()
            "label_lon" -> labelLon = json.readDouble()
            "label_lat" -> labelLat = json.readDouble()
            else -> json.skipValue()
          }
        }
        "geometry" -> json.readObject { member ->
          if (member == "coordinates") polygons = readCoordinates(json) else json.skipValue()
        }
        else -> json.skipValue()
      }
    }
    require(polygons.isNotEmpty()) { "feature $code has no polygon" }
    require(!labelLon.isNaN() && !labelLat.isNaN()) { "feature $code has no label point" }
    return Region(
      code = requireNotNull(code) { "feature without code" },
      sido = requireNotNull(sido) { "feature $code without sido" },
      name = requireNotNull(name) { "feature $code without name" },
      labelLon = labelLon,
      labelLat = labelLat,
      polygons = polygons,
    )
  }

  /** The nesting depth tells Polygon (3) from MultiPolygon (4), independent of member order. */
  private fun readCoordinates(json: JsonCursor): List<RegionPolygon> = when (val depth = json.arrayDepth()) {
    3 -> listOf(readPolygon(json))
    4 -> buildList { json.readArray { add(readPolygon(json)) } }
    else -> throw IllegalArgumentException("unsupported geometry (coordinate depth $depth)")
  }

  private fun readPolygon(json: JsonCursor): RegionPolygon {
    val rings = ArrayList<DoubleArray>(1)
    json.readArray { rings += readRing(json) }
    return RegionPolygon(rings)
  }

  private fun readRing(json: JsonCursor): DoubleArray {
    var coords = DoubleArray(256)
    var size = 0
    json.readArray {
      var axis = 0
      json.readArray {
        val value = json.readDouble()
        if (axis < 2) {
          if (size == coords.size) coords = coords.copyOf(size * 2)
          coords[size++] = value
        }
        axis++
      }
      require(axis >= 2) { "position with fewer than two numbers" }
    }
    return coords.copyOf(size)
  }
}
