package ch.epfl.sdp.ui.maps.offline

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import ch.epfl.sdp.R
import ch.epfl.sdp.map.MapUtils
import ch.epfl.sdp.map.offline.DownloadProgressBarUtils.downloadingInProgress
import ch.epfl.sdp.map.offline.DownloadProgressBarUtils.endProgress
import ch.epfl.sdp.map.offline.DownloadProgressBarUtils.startProgress
import ch.epfl.sdp.map.offline.OfflineRegionUtils.getRegionName
import ch.epfl.sdp.map.offline.OfflineRegionUtils.showErrorAndToast
import ch.epfl.sdp.ui.maps.MapViewBaseActivity
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.offline.*
import com.mapbox.mapboxsdk.offline.OfflineManager.CreateOfflineRegionCallback
import com.mapbox.mapboxsdk.offline.OfflineManager.ListOfflineRegionsCallback
import com.mapbox.mapboxsdk.offline.OfflineRegion.OfflineRegionObserver
import org.json.JSONObject
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Download, view, navigate to, and delete an offline region.
 *
 * Be careful, the maximum number of tiles a user can download is 6000
 * TODO : show error when user try to download more than the limit
 */
class OfflineManagerActivity : MapViewBaseActivity(), OnMapReadyCallback {
    private lateinit var mapboxMap: MapboxMap
    private lateinit var downloadButton: Button
    private lateinit var cancelButton: Button
    private lateinit var offlineManager: OfflineManager
    private lateinit var progressBar: ProgressBar

    companion object {
        // JSON encoding/decoding
        const val JSON_CHARSET = "UTF-8"
        const val JSON_FIELD_REGION_NAME = "FIELD_REGION_NAME"
        const val MAX_ZOOM = 20.0  //  val maxZoom = map!!.maxZoomLevel //max Zoom is 25.5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.initMapView(savedInstanceState, R.layout.activity_offline_manager, R.id.mapView)
        mapView.getMapAsync(this)

        mapView.contentDescription = getString(R.string.map_not_ready)

        // Assign progressBar for later use
        progressBar = findViewById(R.id.progress_bar)
        downloadButton = findViewById(R.id.download_button)
        cancelButton = findViewById(R.id.cancel_download)

        // Set up the offlineManager
        offlineManager = OfflineManager.getInstance(this@OfflineManagerActivity)
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        mapboxMap.setStyle(Style.MAPBOX_STREETS) {
            mapboxMap.cameraPosition = MapUtils.getLastCameraState()

            // Used to detect when the map is ready in tests
            mapView.contentDescription = getString(R.string.map_ready)
        }
    }

    override fun onPause() {
        super.onPause()
        MapUtils.saveCameraPositionAndZoomToPrefs(mapboxMap.cameraPosition)
    }

    fun downloadRegionDialog(v: View) {
        DownloadRegionDialogFragment().show(supportFragmentManager, this.getString(R.string.download_region_dialog_fragment))
    }

    /**
     * @param regionName : String
     * From the current map area style and bundaries,
     * this function creates an OfflineTilePyramidRegionDefinition and
     * a metadata variable.
     * Both are then used to create an OfflineRegion
     * and launch the download
     */
    fun prepareAndLaunchDownload(regionName: String) {
        startProgress(downloadButton, cancelButton, progressBar)
        // Create offline definition using the current
        // style and boundaries of visible map area
        mapboxMap.getStyle { style ->
            val definition = OfflineTilePyramidRegionDefinition(
                    style.uri,
                    mapboxMap.projection.visibleRegion.latLngBounds,
                    mapboxMap.cameraPosition.zoom, MAX_ZOOM,
                    this@OfflineManagerActivity.resources.displayMetrics.density)
            // Build a JSONObject using the user-defined offline region title,
            // convert it into string, and use it to create a metadata variable.
            // The metadata variable will later be passed to createOfflineRegion()
            val metadata = try {
                val jsonObject = JSONObject().put(JSON_FIELD_REGION_NAME, regionName)
                jsonObject.toString().toByteArray(charset(JSON_CHARSET))
            } catch (exception: Exception) {
                showErrorAndToast("Failed to encode metadata: " + exception.message)
                null
            }

            // Create the offline region and launch the download
            offlineManager.createOfflineRegion(definition, metadata!!, object : CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    launchDownload(offlineRegion)
                }

                override fun onError(error: String) {
                    showErrorAndToast("Error : $error")
                }
            })
        }
    }

    private fun launchDownload(offlineRegion: OfflineRegion) { // Set up an observer to handle download progress and
        // notify the user when the region is finished downloading
        mapView.contentDescription = getString(R.string.map_downloading)
        offlineRegion.setObserver(object : OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) { // Compute a percentage
                val percentage = if (status.requiredResourceCount >= 0) 100.0 * status.completedResourceCount / status.requiredResourceCount else 0.0
                if (status.isComplete) { // Download complete
                    endProgress(downloadButton, cancelButton, progressBar)
                    mapView.contentDescription = getString(R.string.map_ready)
                    return
                } else if (status.isRequiredResourceCountPrecise) { // Switch to determinate state
                    downloadingInProgress(percentage.roundToInt(), progressBar)
                }
            }

            override fun onError(error: OfflineRegionError) {
                Timber.e("onError reason: %s", error.reason)
                showErrorAndToast("onError message: " + error.message)
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                showErrorAndToast("Mapbox tile count limit exceeded : $limit")
            }
        })
        // Change the region state
        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }

    fun downloadedRegionList(v: View) { // Build a region list when the user clicks the list button
        // Query the DB asynchronously
        offlineManager.listOfflineRegions(object : ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>) { // Check result. If no regions have been
                // downloaded yet, notify user and return
                if (offlineRegions.isEmpty()) {
                    Toast.makeText(applicationContext, getString(R.string.toast_no_regions_yet), Toast.LENGTH_SHORT).show()
                    return
                }
                // Add all of the region names to a list
                val items = offlineRegions
                        .map { region ->
                            try {
                                getRegionName(region)
                            } catch (exception: java.lang.Exception) {
                                String.format(getString(R.string.region_name_error), region.id)
                            }
                        }
                        .toTypedArray<CharSequence>()
                // Build a dialog containing the list of regions
                ListOfflineRegionDialogFragment(items, offlineRegions, mapboxMap, progressBar, mapView)
                        .show(supportFragmentManager, applicationContext.getString(R.string.list_offline_region_dialog_fragment))
            }

            override fun onError(error: String) {
                showErrorAndToast("Error : $error")
            }
        })
    }
}