package ch.epfl.sdp.mission

import ch.epfl.sdp.searcharea.QuadrilateralArea
import com.mapbox.mapboxsdk.geometry.LatLng
import net.mastrgamr.mbmapboxutils.SphericalUtil.computeOffset
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.lang.Double.max
import kotlin.math.abs

class SimpleQuadStrategyTest {
    @Test(expected = IllegalArgumentException::class)
    fun doesNotAcceptNegativeMaxDistance() {
        SimpleQuadStrategy(-10.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun doesNotAcceptZeroMaxDistance() {
        SimpleQuadStrategy(0.0)
    }

    @Test(expected = java.lang.IllegalArgumentException::class)
    fun doesNotAcceptIncompleteSearchArea() {
        val searchArea = QuadrilateralArea(listOf(LatLng(0.0, 0.0)))
        SimpleQuadStrategy(10.0)
                .createFlightPath(LatLng(0.0, 0.0), searchArea)
    }

    @Test
    fun createsCorrectNumberOfPointsForSmallArea() {
        val searchArea = QuadrilateralArea(listOf(
                LatLng(0.0, 0.0),
                LatLng(1.0, 0.0),
                LatLng(1.0, 1.0),
                LatLng(0.0, 1.0)))
        val strategy = SimpleQuadStrategy(1000000000000.0)
        val path = strategy.createFlightPath(LatLng(0.0, 0.0), searchArea)
        val pathSize = path.size
        assertThat(pathSize, equalTo(4))
    }

    @Test
    fun computeMaxDistOutputsMaximumDistanceHorizontal() {
        val startSide1 = LatLng(0.0, 0.0)
        val startSide2 = LatLng(1.0, 1.0)
        val dist1 = 10.0
        val dist2 = 20.0
        val waypoints = listOf<LatLng>(
                startSide1,
                computeOffset(startSide1, dist1, 19.0),
                startSide2,
                computeOffset(startSide2, dist2, 75.0)
        )
        val res = SimpleQuadStrategy.computeMaxDist(waypoints, SimpleQuadStrategy.Orientation.HORIZONTAL)
        val theoryRes = max(dist1, dist2)
        val diff = abs(res - theoryRes)
        assertThat("Wanted $theoryRes, but got: $res", diff < 0.01)
    }

    @Test
    fun computeMaxDistOutputsMaximumDistanceVertical() {
        val startSide1 = LatLng(0.0, 0.0)
        val startSide2 = LatLng(1.0, 1.0)
        val dist1 = 10.0
        val dist2 = 20.0
        val waypoints = listOf<LatLng>(
                startSide1,
                startSide2,
                computeOffset(startSide2, dist1, 19.0),
                computeOffset(startSide1, dist2, 75.0)
        )
        val res = SimpleQuadStrategy.computeMaxDist(waypoints, SimpleQuadStrategy.Orientation.VERTICAL)
        val theoryRes = max(dist1, dist2)
        val diff = abs(res - theoryRes)
        assertThat("Wanted $theoryRes, but got: $res", diff < 0.01)
    }

    @Test
    fun missionStartsWithClosestPoint() {
        val waypoints = arrayListOf(
                LatLng(47.397026, 8.543067), //we consider the closest point to the drone
                LatLng(47.398979, 8.543434),
                LatLng(47.398279, 8.543934),
                LatLng(47.397426, 8.544867)
        )

        val searchArea = QuadrilateralArea(waypoints
        )
        val dronePos = LatLng(47.4, 8.6)
        val strategy = SimpleQuadStrategy(20.0)
        val path = strategy.createFlightPath(dronePos, searchArea)
        val orderedSearchArea = waypoints.sortedBy { it.distanceTo(dronePos) }
        assertThat(path[0], equalTo(orderedSearchArea[0]))
    }
}