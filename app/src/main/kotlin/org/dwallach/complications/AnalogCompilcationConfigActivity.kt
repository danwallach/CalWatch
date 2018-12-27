/*
 * CalWatch Complications Support
 * Copyright (C) 2017-2018 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
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
import androidx.appcompat.app.AppCompatActivity
import org.dwallach.R
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.info


/**
 * The watch-side config activity for the watchface, which
 * allows for setting the left and right complications of watch face along with other goodies.
 */
class AnalogComplicationConfigActivity : AppCompatActivity(), AnkoLogger {

    private var mWearableRecyclerView: WearableRecyclerView? = null // TODO: replace with non-deprecated variant
    private var mAdapter: AnalogComplicationConfigRecyclerViewAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        info("analog config activity: onCreate")
        setContentView(R.layout.activity_analog_complication_config)

        mAdapter = AnalogComplicationConfigRecyclerViewAdapter(
                applicationContext,
                ComplicationWrapper.watchFaceClass,
                AnalogComplicationConfigData.getDataToPopulateAdapter(this))

        mWearableRecyclerView = findViewById(R.id.wearable_recycler_view)

        // Aligns the first and last items on the list vertically centered on the screen.
//        mWearableRecyclerView?.centerEdgeItems = true

        // Improves performance because we know changes in content do not change the layout size of
        // the RecyclerView.
        mWearableRecyclerView?.setHasFixedSize(true)

        mWearableRecyclerView?.adapter = mAdapter
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        info("analog config activity: onActivityResult")
        if (data == null) {
            debug { "null intent, nothing to do (requestCode = $requestCode, resultCode = $resultCode)" }
            return
        }

        if (requestCode == COMPLICATION_CONFIG_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // Retrieves information for selected Complication provider.
            val complicationProviderInfo = data.getParcelableExtra<ComplicationProviderInfo>(ProviderChooserIntent.EXTRA_PROVIDER_INFO)
            debug { "Provider:  $complicationProviderInfo" }

            // Updates preview with new complication information for selected complication id.
            // Note: complication id is saved and tracked in the adapter class.
            mAdapter?.updateSelectedComplication(complicationProviderInfo)

        }
    }

    companion object {
        internal const val COMPLICATION_CONFIG_REQUEST_CODE = 1001
    }
}