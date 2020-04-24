package ch.epfl.sdp.ui.offlineMapsManaging

import android.widget.ProgressBar
import android.widget.Toast
import ch.epfl.sdp.MainApplication
import ch.epfl.sdp.MapActivity.Companion.MAP_DELETING_DESCRIPTION
import ch.epfl.sdp.MapActivity.Companion.MAP_READY_DESCRIPTION
import ch.epfl.sdp.R
import ch.epfl.sdp.ui.offlineMapsManaging.DownloadProgressBarUtils.hideProgressBar
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.offline.OfflineRegion
import org.json.JSONObject
import timber.log.Timber
import java.nio.charset.Charset


object OfflineRegionUtils {
    fun deleteOfflineRegion(offRegion: OfflineRegion,progressBar : ProgressBar, mapView: MapView) {
        mapView.contentDescription = MAP_DELETING_DESCRIPTION
        offRegion.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() { // Once the region is deleted, remove the
                // progressBar and display a toast
                hideProgressBar(progressBar)
                val context = MainApplication.applicationContext()
                Toast.makeText(context, context.getString(R.string.toast_region_deleted), Toast.LENGTH_LONG).show()
                mapView.contentDescription = MAP_READY_DESCRIPTION
            }

            override fun onError(error: String) {
                hideProgressBar(progressBar)
                showErrorAndToast("Error : $error")
            }
        })
    }

    // Get the region name from the offline region metadata
    fun getRegionName(offlineRegion: OfflineRegion): String {
        val regionName = try {
            JSONObject(String(offlineRegion.metadata, Charset.forName(OfflineManagerActivity.JSON_CHARSET)))
                    .getString(OfflineManagerActivity.JSON_FIELD_REGION_NAME)
        } catch (exception: Exception) {
            Timber.e("Failed to decode metadata: %s", exception.message)
            String.format(MainApplication.applicationContext().getString(R.string.region_name), offlineRegion.id)
        }
        return regionName
    }

    fun showErrorAndToast(message : String){
        Timber.e(message)
        Toast.makeText(MainApplication.applicationContext(), message, Toast.LENGTH_LONG).show()
    }
}