package ch.epfl.sdp

import com.mapbox.mapboxsdk.geometry.LatLng
import io.mavsdk.mission.Mission

object DroneMission {
    private val missionItems = arrayListOf<Mission.MissionItem>()

    fun makeDroneMission(path: List<LatLng>): DroneMission {
        addMissionItems(path)
        return this
    }

    private fun addMissionItems(path: List<LatLng>) {
        path.forEach { point ->
            missionItems.add(generateMissionItem(point.latitude, point.longitude))
        }
    }

    fun generateMissionItem(latitudeDeg: Double, longitudeDeg: Double): Mission.MissionItem {
        return Mission.MissionItem(
                latitudeDeg,
                longitudeDeg,
                10f,
                10f,
                true, Float.NaN, Float.NaN,
                Mission.MissionItem.CameraAction.NONE, Float.NaN,
                1.0)
    }

    fun getMissionItems(): ArrayList<Mission.MissionItem> {
        return missionItems
    }
}
