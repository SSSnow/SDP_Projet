package ch.epfl.sdp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Observer
import ch.epfl.sdp.drone.Drone
import ch.epfl.sdp.drone.SimpleMultiPassOnQuadrilateral
import ch.epfl.sdp.map.*
import ch.epfl.sdp.ui.maps.MapUtils
import ch.epfl.sdp.ui.maps.MapViewBaseActivity
import ch.epfl.sdp.ui.offlineMapsManaging.OfflineManagerActivity
import com.getbase.floatingactionbutton.FloatingActionButton
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.Circle
import com.mapbox.mapboxsdk.plugins.annotation.CircleManager
import com.mapbox.mapboxsdk.plugins.annotation.CircleOptions
import com.mapbox.mapboxsdk.plugins.annotation.FillOptions
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.sources.GeoJsonOptions
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.utils.ColorUtils

/**
 * Main Activity to display map and create missions.
 * 1. Take off
 * 2. Long click on map to add a waypoint
 * 3. Hit play to start mission.
 */
class MapActivity : MapViewBaseActivity(), OnMapReadyCallback {

    private var isMapReady = false
    private var isDroneFlying = false

    private lateinit var droneCircleManager: CircleManager
    private lateinit var userCircleManager: CircleManager
    private lateinit var dronePositionMarker: Circle
    private lateinit var userPositionMarker: Circle

    var waypoints = arrayListOf<LatLng>()

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var features = ArrayList<Feature>()

    private lateinit var geoJsonSource: GeoJsonSource
    private lateinit var mapboxMap: MapboxMap

    private lateinit var droneBatteryLevelImageView: ImageView
    private lateinit var droneBatteryLevelTextView: TextView
    private lateinit var distanceToUserTextView: TextView
    private lateinit var droneAltitudeTextView: TextView
    private lateinit var droneSpeedTextView: TextView

    /** Builders */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var searchAreaBuilder: SearchAreaBuilder

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var missionBuilder: MissionBuilder

    /** Painters */
    private lateinit var searchAreaPainter: MapBoxSearchAreaPainter
    private lateinit var missionPainter: MapBoxMissionPainter

    private val droneBatteryLevelDrawables = listOf(
            Pair(.0, R.drawable.ic_battery1),
            Pair(.05, R.drawable.ic_battery2),
            Pair(.23, R.drawable.ic_battery3),
            Pair(.41, R.drawable.ic_battery4),
            Pair(.59, R.drawable.ic_battery5),
            Pair(.77, R.drawable.ic_battery6),
            Pair(.95, R.drawable.ic_battery7)
    )

    private var dronePositionObserver = Observer<LatLng> { newLatLng: LatLng? ->
        newLatLng?.let { updateDronePosition(it); updateDronePositionOnMap(it) }
    }
    private var userPositionObserver = Observer<LatLng> { newLatLng: LatLng? ->
        newLatLng?.let { updateUserPosition(it); updateUserPositionOnMap(it) }
    }

    private var droneBatteryObserver = Observer<Float> { newBatteryLevel: Float? ->
        updateTextView(droneBatteryLevelTextView, newBatteryLevel?.times(100)?.toDouble(), PERCENTAGE_FORMAT) // Always update the text string
        newBatteryLevel?.let { // Only update the icon if the battery level is not null
            val newBatteryDrawable = droneBatteryLevelDrawables
                    .filter { x -> x.first <= newBatteryLevel.coerceAtLeast(0f) }
                    .maxBy { x -> x.first }!!
                    .second
            droneBatteryLevelImageView.setImageResource(newBatteryDrawable)
            droneBatteryLevelImageView.tag = newBatteryDrawable
        }
    }
    private var droneAltitudeObserver = Observer<Float> { newAltitude: Float? -> updateTextView(droneAltitudeTextView, newAltitude?.toDouble(), DISTANCE_FORMAT) }
    private var droneSpeedObserver = Observer<Float> { newSpeed: Float? -> updateTextView(droneSpeedTextView, newSpeed?.toDouble(), SPEED_FORMAT) }

    companion object {
        const val MAP_NOT_READY_DESCRIPTION: String = "MAP NOT READY"
        const val MAP_READY_DESCRIPTION: String = "MAP READY"

        private const val DISTANCE_FORMAT = " %.1f m"
        private const val PERCENTAGE_FORMAT = " %.0f%%"
        private const val SPEED_FORMAT = " %.1f m/s"
        private const val REGION_FILL_OPACITY = 0.5f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.initMapView(savedInstanceState, R.layout.activity_map, R.id.mapView)
        mapView.getMapAsync(this)
        initButtons()

        droneBatteryLevelImageView = findViewById(R.id.battery_level_icon)
        droneBatteryLevelTextView = findViewById(R.id.battery_level)
        droneAltitudeTextView = findViewById(R.id.altitude)
        distanceToUserTextView = findViewById(R.id.distance_to_user)
        droneSpeedTextView = findViewById(R.id.speed)

        mapView.contentDescription = MAP_NOT_READY_DESCRIPTION

        CentralLocationManager.configure(this)
    }

    private fun initButtons(){
        findViewById<FloatingActionButton>(R.id.start_or_return_button).setOnClickListener {
            if(!isDroneFlying) { //TODO : return to user else
                isDroneFlying = true
                Drone.startMission(DroneMission.makeDroneMission(
                        missionBuilder.build()
                ).getMissionItems())
            }
            updateStartButton()
        }
        findViewById<FloatingActionButton>(R.id.clear_button ).setOnClickListener { clearWaypoints() }
        findViewById<FloatingActionButton>(R.id.locate_button).setOnClickListener { centerCameraOnDrone() }
        findViewById<FloatingActionButton>(R.id.store_button ).setOnClickListener { startActivity(Intent(applicationContext, OfflineManagerActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        Drone.currentPositionLiveData.observe(this, dronePositionObserver)
        Drone.currentBatteryLevelLiveData.observe(this, droneBatteryObserver)
        Drone.currentAbsoluteAltitudeLiveData.observe(this, droneAltitudeObserver)
        Drone.currentSpeedLiveData.observe(this, droneSpeedObserver)
        CentralLocationManager.currentUserPosition.observe(this, userPositionObserver)

    }

    override fun onPause() {
        super.onPause()
        CentralLocationManager.currentUserPosition.removeObserver(userPositionObserver)
        Drone.currentPositionLiveData.removeObserver(dronePositionObserver)
        Drone.currentBatteryLevelLiveData.removeObserver(droneSpeedObserver)
        Drone.currentAbsoluteAltitudeLiveData.removeObserver(droneAltitudeObserver)
        Drone.currentSpeedLiveData.removeObserver(droneSpeedObserver)

        if (isMapReady) MapUtils.saveCameraPositionAndZoomToPrefs(mapboxMap.cameraPosition)
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap

        mapboxMap.setStyle(Style.MAPBOX_STREETS) { style ->
            droneCircleManager = CircleManager(mapView, mapboxMap, style)
            userCircleManager = CircleManager(mapView, mapboxMap, style)
            missionPainter = MapBoxMissionPainter(mapView, mapboxMap, style)
            searchAreaPainter = MapBoxQuadrilateralPainter(mapView, mapboxMap, style)

            mapboxMap.addOnMapClickListener {
                onMapClicked(it)
                true
            }
            mapboxMap.addOnMapLongClickListener {
                onMapLongClicked(it)
                true
            }

            geoJsonSource = GeoJsonSource(getString(R.string.heatmap_source_ID), GeoJsonOptions()
                    .withCluster(true)
                    .withClusterProperty("intensities", Expression.literal("+"), Expression.get("intensity"))
                    .withClusterMaxZoom(13)
            )
            geoJsonSource.setGeoJson(FeatureCollection.fromFeatures(features))

            style.addSource(geoJsonSource)
            MapUtils.createLayersForHeatMap(style)
            mapboxMap.cameraPosition = MapUtils.getLastCameraState()// Load latest location
            mapView.contentDescription = MAP_READY_DESCRIPTION// Used to detect when the map is ready in tests

            //Create builders
            missionBuilder = MissionBuilder()
                    .withStartingLocation(LatLng(MapUtils.DEFAULT_LATITUDE, MapUtils.DEFAULT_LONGITUDE))
                    .withStrategy(SimpleMultiPassOnQuadrilateral(Drone.GROUND_SENSOR_SCOPE))
            searchAreaBuilder = QuadrilateralBuilder()


            // Add listeners to builders
            searchAreaBuilder.searchAreaChanged.add { missionBuilder.withSearchArea(it) }
            searchAreaBuilder.verticesChanged.add { searchAreaPainter.paint(it) }
            missionBuilder.generatedMissionChanged.add { missionPainter.paint(it) }
            searchAreaPainter.onMoveVertex.add { old, new -> searchAreaBuilder.moveVertex(old, new) }

            // Location listener on drone
            Drone.currentPositionLiveData.observe(this, Observer { missionBuilder.withStartingLocation(it) })

            isMapReady = true
            /**Uncomment this to see a virtual heatmap, if uncommented, tests won't pass**/
            //addVirtualPointsToHeatmap()
        }
    }

    /**
     * Updates the text of the given textView with the given value and format, or the default string
     * if the value is null
     */
    private fun updateTextView(textView: TextView, value: Double?, formatString: String) {
        textView.text = value?.let { formatString.format(it) } ?: getString(R.string.no_info)
    }

    fun onMapClicked(position: LatLng) {
        try {
            searchAreaBuilder.addVertex(position)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Map long clic to current eventListener
     */
    fun onMapLongClicked(position: LatLng) {}

    /**
     * Clears the waypoints list and removes all the lines and points related to waypoints
     */
    private fun clearWaypoints() {
        if (!isMapReady) return
        searchAreaBuilder.reset()
    }

    /**
     * Adds a heat point to the heatmap
     */
    fun addPointToHeatMap(longitude: Double, latitude: Double, intensity: Double) {
        if (!isMapReady) return
        var feature: Feature = Feature.fromGeometry(Point.fromLngLat(longitude, latitude))
        feature.addNumberProperty("intensity", intensity)
        /* Will be needed when we have the signal of the drone implemented */
        //feature.addNumberProperty("intensity", Drone.getSignalStrength())
        features.add(feature)
        geoJsonSource.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /**
     * Update [currentPositionMarker] position with a new [position].
     *
     * @param newLatLng new position of the vehicle
     */
    private fun updateDronePosition(newLatLng: LatLng) {
        CentralLocationManager.currentUserPosition.value?.let {
            updateTextView(distanceToUserTextView, it.distanceTo(newLatLng), DISTANCE_FORMAT)
        }
    }

    private fun updateDronePositionOnMap(newLatLng: LatLng) {
        if (!isMapReady) return

        // Add a vehicle marker and move the camera
        if (!::dronePositionMarker.isInitialized) {
            val circleOptions = CircleOptions()
                    .withLatLng(newLatLng)
                    .withCircleColor(ColorUtils.colorToRgbaString(Color.RED))
            dronePositionMarker = droneCircleManager.create(circleOptions)

//            mapboxMap.moveCamera(CameraUpdateFactory.tiltTo(0.0))
//            mapboxMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, 14.0))
        } else {
            dronePositionMarker.latLng = newLatLng
            droneCircleManager.update(dronePositionMarker)
        }
    }

    /**
     * Centers the camera on the drone
     */
    private fun centerCameraOnDrone(){
        if(::dronePositionMarker.isInitialized){
        mapboxMap.moveCamera(CameraUpdateFactory.newLatLngZoom(dronePositionMarker.latLng, 14.0))
        }
    }

    private fun updateUserPositionOnMap(userLatLng: LatLng) {
        if (!isMapReady) return

        // Add a vehicle marker and move the camera
        if (!::userPositionMarker.isInitialized) {
            val circleOptions = CircleOptions()
                    .withLatLng(userLatLng)
            userPositionMarker = userCircleManager.create(circleOptions)
        } else {
            userPositionMarker.latLng = userLatLng
            userCircleManager.update(userPositionMarker)
        }
    }

    private fun updateUserPosition(userLatLng: LatLng) {
        Drone.currentPositionLiveData.value?.let {
            updateTextView(distanceToUserTextView, it.distanceTo(userLatLng), DISTANCE_FORMAT)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        CentralLocationManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun updateStartButton(){
        findViewById<FloatingActionButton>(R.id.start_or_return_button)
                .setIcon(if (isDroneFlying) R.drawable.ic_return else R.drawable.ic_start )
    }
}