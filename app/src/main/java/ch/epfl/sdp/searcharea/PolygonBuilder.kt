package ch.epfl.sdp.searcharea

import com.mapbox.mapboxsdk.geometry.LatLng

class PolygonBuilder : SearchAreaBuilder() {
    override val sizeLowerBound: Int? = 3
    override val sizeUpperBound: Int? = null
    override val shapeName: String = "Polygon"
    override fun buildIfComplete(): PolygonArea = PolygonArea(vertices)
}