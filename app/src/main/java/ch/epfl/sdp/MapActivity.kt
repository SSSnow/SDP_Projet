package ch.epfl.sdp

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import ch.epfl.sdp.drone.Drone
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.MultiPoint
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.Circle
import com.mapbox.mapboxsdk.plugins.annotation.CircleManager
import com.mapbox.mapboxsdk.plugins.annotation.CircleOptions
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.expressions.Expression.*
import com.mapbox.mapboxsdk.style.layers.CircleLayer
import com.mapbox.mapboxsdk.style.layers.HeatmapLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.sources.GeoJsonOptions
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource


/**
 * Main Activity to display map and create missions.
 * 1. Take off
 * 2. Long click on map to add a waypoint
 * 3. Hit play to start mission.
 */
class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private var mapView: MapView? = null
    private var mapboxMap: MapboxMap? = null
    private var heatmapLayer: HeatmapLayer? = null

    private var circleManager: CircleManager? = null
    private var symbolManager: SymbolManager? = null
    private var currentPositionMarker: Circle? = null
    private val heatMapLayerID = "heatMapLayerID"
    private val heatMapSourceID = "heatMapSourceID"

    private var currentPositionObserver = Observer<LatLng> { newLatLng: LatLng? -> newLatLng?.let { updateVehiclePosition(it) } }
    //private var currentMissionPlanObserver = Observer { latLngs: List<LatLng> -> updateMarkers(latLngs) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mapbox access token is configured here. This needs to be called either in your application
        // object or in the same activity which contains the mapview.
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)

        val button: Button = findViewById(R.id.start_mission_button)
        button.setOnClickListener {
            val dme = DroneMissionExample.makeDroneMission()
            dme.startMission()
        }
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()

        Drone.currentPositionLiveData.observe(this, currentPositionObserver)
        // viewModel.currentMissionPlanLiveData.observe(this, currentMissionPlanObserver)
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()

        Drone.currentPositionLiveData.removeObserver(currentPositionObserver)
        //Mission.currentMissionPlanLiveData.removeObserver(currentMissionPlanObserver)
    }

    override fun onStop() {
        super.onStop()
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString("latitude", mapboxMap?.cameraPosition?.target?.latitude.toString())
                .putString("longitude", mapboxMap?.cameraPosition?.target?.longitude.toString())
                .putString("zoom", mapboxMap?.cameraPosition?.zoom.toString())
                .apply();
        mapView?.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        mapboxMap.setStyle(Style.MAPBOX_STREETS) { style ->
            // Add the marker image to map
//            style.addImage("marker-icon-id",
//                    BitmapFactory.decodeResource(
//                            this@MapsActivity.resources, R.drawable.mapbox_marker_icon_default))
            symbolManager = mapView?.let { SymbolManager(it, mapboxMap, style) }
            symbolManager!!.iconAllowOverlap = true
            circleManager = mapView?.let { CircleManager(it, mapboxMap, style) }
            createLoadGeoJsonData(style)
        }

        // Load latest location
        val latitude: Double = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("latitude", null)?.toDoubleOrNull() ?: -52.6885
        val longitude: Double = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("longitude", null)?.toDoubleOrNull() ?: -70.1395
        val zoom: Double = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("zoom", null)?.toDoubleOrNull() ?: 9.0

        mapboxMap.cameraPosition = CameraPosition.Builder()
                .target(LatLng(latitude, longitude))
                .zoom(zoom)
                .build()

        mapboxMap.addOnMapClickListener { point ->
            mapboxMap.addMarker(MarkerOptions().position(point).title(point.toString()))
            true
        }
        mapboxMap.setOnMarkerClickListener { marker ->
            mapboxMap.removeMarker(marker)
            true
        }
//        mapboxMap.uiSettings.isRotateGesturesEnabled = false
//        mapboxMap.uiSettings.isTiltGesturesEnabled = false
        // Allow to pinpoint
//        mapboxMap.addOnMapLongClickListener { point: LatLng? ->
//            viewModel.addWaypoint(point)
//            true
//        }
    }
    private fun createLoadGeoJsonData(style: Style){
        var featureCollection : FeatureCollection
        val points = ArrayList<Point>()
        points.add(Point.fromLngLat(8.543934,47.398279))
        points.add(Point.fromLngLat(8.544867,47.397426))
        if (points.size>=2) {
            val multiPoints = MultiPoint.fromLngLats(points)
            featureCollection = FeatureCollection.fromFeature(Feature.fromGeometry(multiPoints))
            style.addSource(GeoJsonSource(heatMapSourceID, featureCollection, GeoJsonOptions().withMaxZoom(40)))
        }
        else if (points.size==1){
            val point = points[0]
            featureCollection = FeatureCollection.fromFeature(Feature.fromGeometry(point))
            style.addSource(GeoJsonSource(heatMapSourceID, featureCollection, GeoJsonOptions().withCluster(true)))
            //Here the GeoJsonOption withCluster() works because the geometry is point and not a multiPoint
        }
        else{
            /** NO POINTS DETECTED**/
        }

        /**UNCOMMENT THIS WHEN CLUSTER WORKS**/
        /*
        val layers = arrayOf(
                intArrayOf(2, Color.parseColor("#E55E5E")),
                intArrayOf(1, Color.parseColor("#F9886C")),
                intArrayOf(0, Color.parseColor("#FBB03B")))

        val unclustered = CircleLayer("unclustered-points", heatMapSourceID)
        unclustered.setProperties(
                circleColor(Color.parseColor("#FBB03B")),
                circleRadius(20f),
                circleBlur(1f))
        unclustered.setFilter(Expression.neq(get("cluster"), literal(true)))
        style.addLayerBelow(unclustered, "building")



        for (i in layers.indices) {
            val circles = CircleLayer("cluster-$i", heatMapSourceID)
            circles.setProperties(
                    circleColor(layers[i][1]),
                    circleRadius(70f),
                    circleBlur(1f)
            )
            val pointCount: Expression = toNumber(get("point_count"))
            circles.setFilter(
                    if (i == 0) Expression.gte(pointCount, literal(layers[i][0])) else Expression.all(
                            Expression.gte(pointCount, literal(layers[i][0])),
                            Expression.lt(pointCount, literal(layers[i - 1][0]))
                    )
            )
            style.addLayerBelow(circles, "building")
        }
        */
        /**DELETE THIS WHEN CLUSTER WORKS**/
        val layers = arrayOf(
                intArrayOf(150, Color.parseColor("#E55E5E")),
                intArrayOf(20, Color.parseColor("#F9886C")),
                intArrayOf(0, Color.parseColor("#FBB03B")))

        for (i in layers.indices) { //Add clusters' circles
            val circles = CircleLayer("cluster-$i", heatMapSourceID)
            circles.setProperties(
                    circleColor(layers[i][1]),
                    circleRadius(30f),
                    circleBlur(1f)
            )
            style.addLayer(circles)
        }
  }

    /** FOR THE MENU IF NEEDED **/
//    override fun _onCreateOptionsMenu(menu: Menu?): Boolean {
//        menuInflater.inflate(R.menu.menu_maps, menu)
//        return true
//    }

//    override fun _onOptionsItemSelected(item: MenuItem): Boolean {
//        // Handle item selection
//        when (item.getItemId()) {
//            R.id.disarm -> drone.getAction().kill().subscribe()
//            R.id.land -> drone.getAction().land().subscribe()
//            R.id.return_home -> drone.getAction().returnToLaunch().subscribe()
//            R.id.takeoff -> drone.getAction().arm().andThen(drone.getAction().takeoff()).subscribe()
//            else -> return super.onOptionsItemSelected(item)
//        }
//        return true
//    }

    /**
     * Update [currentPositionMarker] position with a new [position].
     *
     * @param newLatLng new position of the vehicle
     */
    private fun updateVehiclePosition(newLatLng: LatLng) {
        if (mapboxMap == null || circleManager == null) {
            // Not ready
            return
        }

        // Add a vehicle marker and move the camera
        if (currentPositionMarker == null) {
            val circleOptions = CircleOptions()
            circleOptions.withLatLng(newLatLng)
            currentPositionMarker = circleManager!!.create(circleOptions)

            mapboxMap!!.moveCamera(CameraUpdateFactory.tiltTo(0.0))
            mapboxMap!!.moveCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, 14.0))
        } else {
            currentPositionMarker!!.latLng = newLatLng
            circleManager!!.update(currentPositionMarker)
        }
    }

//    /**
//     * Update the [map] with the current mission plan waypoints.
//     *
//     * @param latLngs current mission waypoints
//     */
//    private fun updateMarkers(latLngs: List<LatLng>) {
//        if (circleManager != null) {
//            circleManager!!.delete(waypoints)
//            waypoints.clear()
//        }
//        for (latLng in latLngs) {
//            val circleOptions: CircleOptions = CircleOptions()
//                    .withLatLng(latLng)
//                    .withCircleColor(ColorUtils.colorToRgbaString(Color.BLUE))
//                    .withCircleStrokeColor(ColorUtils.colorToRgbaString(Color.BLACK))
//                    .withCircleStrokeWidth(1.0f)
//                    .withCircleRadius(12f)
//                    .withDraggable(false)
//            circleManager?.create(circleOptions)
//        }
//    }
}
