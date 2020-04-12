package ch.epfl.sdp.searcharea

import androidx.lifecycle.MutableLiveData
import com.mapbox.mapboxsdk.geometry.LatLng

class PolygonArea : SearchArea {

    private val angles: MutableLiveData<MutableList<LatLng>> = MutableLiveData(mutableListOf())

    fun getNbAngles(): Int {
        return angles.value?.size!!
    }

    fun addAngle(angle: LatLng) {
        angles.value?.add(angle)
        angles.notifyObserver()
    }

    override fun isComplete(): Boolean {
        return angles.value?.size!! >= 3
    }

    override fun getLatLng(): MutableLiveData<MutableList<LatLng>> {
        return angles
    }
}