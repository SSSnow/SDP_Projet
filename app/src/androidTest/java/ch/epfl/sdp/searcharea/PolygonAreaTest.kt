package ch.epfl.sdp.searcharea

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapbox.mapboxsdk.geometry.LatLng
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class PolygonAreaTest {

    @Test
    fun CanAddHundredAngles() {
        val size = 100
        val area = PolygonArea()
        for (i in 0..size) {
            area.addAngle(LatLng(Random.nextDouble(), Random.nextDouble()))
        }
        assertThat(area.getNbAngles(), equalTo(size))
    }

    @Test
    fun IsCompleteWhenAtLeastThreeAngle() {
        val area = PolygonArea()
        assertThat(area.isComplete(), equalTo(false))

        area.addAngle(LatLng(Random.nextDouble(), Random.nextDouble()))
        assertThat(area.isComplete(), equalTo(false))

        area.addAngle(LatLng(Random.nextDouble(), Random.nextDouble()))
        assertThat(area.isComplete(), equalTo(false))

        area.addAngle(LatLng(Random.nextDouble(), Random.nextDouble()))
        assertThat(area.isComplete(), equalTo(true))

        area.addAngle(LatLng(Random.nextDouble(), Random.nextDouble()))
        assertThat(area.isComplete(), equalTo(true))
    }

    @Test
    fun ResetIsPerformed() {
        val area = PolygonArea()

        area.addAngle(LatLng(Random.nextDouble(), Random.nextDouble()))
        assertThat(area.getNbAngles(), equalTo(1))
        area.reset()
        assertThat(area.getNbAngles(), equalTo(0))
    }

    @Test
    fun GetPropsShouldBeEmpty() {
        val area = PolygonArea()
        assertThat(area.getAdditionalProps().value, equalTo(mutableMapOf()))
    }
}