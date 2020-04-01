package ch.epfl.sdp.drone

import com.mapbox.mapboxsdk.geometry.LatLng
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.ceil
import kotlin.math.max

interface OverflightStrategy {
    fun createFlightPath(waypoints: List<LatLng>): List<LatLng>
}

/**
 * Creates a path covering a quadrilateral in several passes
 */
class SimpleMultiPassOnQuadrangle(maxDistBetweenLinesIn: Double) : OverflightStrategy{
    private val maxDistBetweenLines: Double
    init {
        require(maxDistBetweenLinesIn > 0.0){
            "The maximum distance between passes must be strictly positive"
        }
        this.maxDistBetweenLines = maxDistBetweenLinesIn
    }
    @Throws(IllegalArgumentException::class)
    override fun createFlightPath(pinpoints: List<LatLng>): List<LatLng> {
        require(pinpoints.size == 4){
            "This strategy requires exactly 4 pinpoints, ${pinpoints.size} given."
        }

        // Make a mutable copy of the waypoints to be able to reorder them
        var waypoints = mutableListOf<LatLng>().apply { addAll(pinpoints) }

        val steps = max(2, ceil(max(
                waypoints[0].distanceTo(waypoints[1]) / maxDistBetweenLines,
                waypoints[2].distanceTo(waypoints[3]) / maxDistBetweenLines)).toInt())

        val path = ArrayList<LatLng>()

        for (step in 0 until steps){
            path.add(generateStepAlong(waypoints[0], waypoints[1], step, steps))
            path.add(generateStepAlong(waypoints[3], waypoints[2], step, steps))
            if(step % 2 != 0){
                Collections.swap(path, path.size-1,path.size-2)
            }
        }
        return path
    }

    /**
     * Generates a LatLng positioned at step/steps in the segment p0---p1
     */
    private fun generateStepAlong(p0: LatLng, p1: LatLng, step: Int, steps: Int): LatLng{
        val stepLat = (p1.latitude - p0.latitude) / (steps - 1)
        val stepLng = (p1.longitude - p0.longitude)  / (steps - 1)
        return LatLng(p0.latitude + step * stepLat, p0.longitude + step * stepLng)
    }
}