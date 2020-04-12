package ch.epfl.sdp.drone

import ch.epfl.sdp.searcharea.SearchArea
import com.mapbox.mapboxsdk.geometry.LatLng
import java.lang.IllegalArgumentException

interface OverflightStrategy {

    fun acceptArea(searchArea: SearchArea): Boolean
    fun createFlightPath(startingPoint: LatLng, searchArea: SearchArea): List<LatLng>
}