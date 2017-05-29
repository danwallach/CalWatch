package org.dwallach.complications

/**
 * This began life as the AnalogComplicationConfigActivity example.
 */
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.wearable.complications.ComplicationProviderInfo
import android.support.wearable.complications.ProviderChooserIntent
import android.support.wearable.view.WearableRecyclerView
import android.util.Log
import org.dwallach.R


/**
 * The watch-side config activity for the watchface, which
 * allows for setting the left and right complications of watch face along with other goodies.
 */
class AnalogComplicationConfigActivity : Activity() {

    private var mWearableRecyclerView: WearableRecyclerView? = null
    private var mAdapter: AnalogComplicationConfigRecyclerViewAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_analog_complication_config)

        mAdapter = AnalogComplicationConfigRecyclerViewAdapter(
                applicationContext,
                ComplicationManager.getWatchFaceService()::class.java,
                AnalogComplicationConfigData.getDataToPopulateAdapter(this))

        mWearableRecyclerView = findViewById(R.id.wearable_recycler_view) as WearableRecyclerView

        // Aligns the first and last items on the list vertically centered on the screen.
        mWearableRecyclerView?.centerEdgeItems = true

        // Improves performance because we know changes in content do not change the layout size of
        // the RecyclerView.
        mWearableRecyclerView?.setHasFixedSize(true)

        mWearableRecyclerView?.adapter = mAdapter
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {

        if (requestCode == COMPLICATION_CONFIG_REQUEST_CODE && resultCode == Activity.RESULT_OK) {

            // Retrieves information for selected Complication provider.
            val complicationProviderInfo = data.getParcelableExtra<ComplicationProviderInfo>(ProviderChooserIntent.EXTRA_PROVIDER_INFO)
            Log.d(TAG, "Provider: " + complicationProviderInfo)

            // Updates preview with new complication information for selected complication id.
            // Note: complication id is saved and tracked in the adapter class.
            mAdapter?.updateSelectedComplication(complicationProviderInfo)

        }
    }

    companion object {
        private val TAG = AnalogComplicationConfigActivity::class.java.simpleName
        internal val COMPLICATION_CONFIG_REQUEST_CODE = 1001
    }
}