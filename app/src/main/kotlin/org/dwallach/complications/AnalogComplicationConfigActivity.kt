/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2019 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.complications

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.wearable.complications.ComplicationProviderInfo
import android.support.wearable.complications.ProviderChooserIntent
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.widget.WearableRecyclerView
import org.dwallach.R
import org.dwallach.calwatch2.Constants
import org.dwallach.calwatch2.PreferencesHelper
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.info

/**
 * The watch-side config activity for the watchface, which
 * allows for setting the left and right complications of watch face along with other goodies.
 *
 * This began life as the AnalogComplicationConfigActivity example code.
 */
class AnalogComplicationConfigActivity : AppCompatActivity(), AnkoLogger {
    private var mAdapter: AnalogComplicationConfigRecyclerViewAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        info("analog config activity: onCreate")
        setContentView(R.layout.activity_analog_complication_config)

        // If the user went straight from installing the watchface to the
        // config dialog, we got an uninitialized property exception on one of our
        // lateinit properties in ComplicationWrapper, which we only originally
        // initialized from CalWatchFaceService.Engine.onCreate(). No good.
        // Solution? Stripped down version of the init code.
        ComplicationWrapper.wimpyInit(Constants.COMPLICATION_LOCATIONS)
        PreferencesHelper.loadPreferences(this)

        val adapterDataList = AnalogComplicationConfigData.getDataToPopulateAdapter(this)

        info { "For adapter data, got ${adapterDataList.size} entries" }

        mAdapter = AnalogComplicationConfigRecyclerViewAdapter(
            applicationContext,
            ComplicationWrapper.watchFaceClass,
            adapterDataList
        )

        val mWearableRecyclerView = findViewById<WearableRecyclerView>(R.id.wearable_recycler_view)

        if (mWearableRecyclerView == null) {
            error("null recycler view!")
        } else {

            // Aligns the first and last items on the list vertically centered on the screen.
//        mWearableRecyclerView?.centerEdgeItems = true

            // Improves performance because we know changes in content do not change the layout size of
            // the RecyclerView.
            mWearableRecyclerView.setHasFixedSize(true)

            mWearableRecyclerView.adapter = mAdapter
            info("recycler view initialized")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        info("analog config activity: onActivityResult")
        if (data == null) {
            debug { "null intent, nothing to do (requestCode = $requestCode, resultCode = $resultCode)" }
            return
        }

        if (requestCode == COMPLICATION_CONFIG_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // Retrieves information for selected Complication provider.
            val complicationProviderInfo =
                data.getParcelableExtra<ComplicationProviderInfo>(ProviderChooserIntent.EXTRA_PROVIDER_INFO)
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
