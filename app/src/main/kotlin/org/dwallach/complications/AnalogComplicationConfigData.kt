/*
 * CalWatch Complications Support
 * Copyright (C) 2017 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.complications

import android.content.Context
import android.support.v7.widget.RecyclerView.ViewHolder
import android.support.v7.widget.RecyclerView

import org.dwallach.R
import org.dwallach.complications.ComplicationLocation.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info

object AnalogComplicationConfigData: AnkoLogger {
    /**
     * Interface all ConfigItems must implement so the [RecyclerView]'s Adapter associated
     * with the configuration activity knows what type of ViewHolder to inflate.
     */
    interface ConfigItemType {
        val configType: Int
    }

    /**
     * Includes all data to populate each of the different custom
     * [ViewHolder] types in [AnalogComplicationConfigRecyclerViewAdapter].
     */
    fun getDataToPopulateAdapter(context: Context): List<ConfigItemType> {
        info("getDataToPopulateAdapter")

        val previewConfigItem = PreviewAndComplicationsConfigItem(R.drawable.add_complication)
        val moreOptionsConfigItem = MoreOptionsConfigItem(R.drawable.ic_expand_more_white_18dp)

        return listOf(previewConfigItem, moreOptionsConfigItem)
    }

    /**
     * Data for Watch Face Preview with Complications Preview item in RecyclerView.
     */
    class PreviewAndComplicationsConfigItem internal constructor(val defaultComplicationResourceId: Int) : ConfigItemType {
        override val configType: Int
            get() = AnalogComplicationConfigRecyclerViewAdapter.TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG
    }

    /**
     * Data for "more options" item in RecyclerView.
     */
    class MoreOptionsConfigItem internal constructor(val iconResourceId: Int) : ConfigItemType {
        override val configType: Int
            get() = AnalogComplicationConfigRecyclerViewAdapter.TYPE_MORE_OPTIONS
    }

    /**
     * Data for background image complication picker item in RecyclerView.
     */
    class BackgroundComplicationConfigItem internal constructor(
            val name: String,
            val iconResourceId: Int) : ConfigItemType {

        override val configType: Int
            get() = AnalogComplicationConfigRecyclerViewAdapter.TYPE_CHANGE_WATCHFACE_STYLE
    }

    /**
     * Data for a toggle switch
     */
    class ToggleConfigItem internal constructor(
            val iconResourceId: Int,
            val displayText: String,
            var enabled: Boolean,
            val callback: (Boolean) -> Unit) : ConfigItemType {


        override val configType: Int
            get() = AnalogComplicationConfigRecyclerViewAdapter.TYPE_TOGGLE
    }
}
