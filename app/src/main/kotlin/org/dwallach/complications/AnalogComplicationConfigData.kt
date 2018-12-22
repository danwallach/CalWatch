/*
 * CalWatch Complications Support
 * Copyright (C) 2017 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.complications

import android.content.Context
//import android.support.v7.widget.RecyclerView.ViewHolder
//import android.support.v7.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder

import org.dwallach.R
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

        return listOf(
                PreviewAndComplicationsConfigItem(R.drawable.add_complication),
                MoreOptionsConfigItem(R.drawable.ic_expand_more_white_18dp),
                WatchfaceChangeStyle(R.drawable.ic_all_faces_icon,
                        context.getString(R.string.watchface_style_selector)))
    }

    /**
     * Data for Watch Face Preview with Complications Preview item in RecyclerView.
     */
    class PreviewAndComplicationsConfigItem(val defaultComplicationResourceId: Int) : ConfigItemType {
        override val configType: Int
            get() = AnalogComplicationConfigRecyclerViewAdapter.TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG
    }

    /**
     * Data for "more options" item in RecyclerView.
     */
    class MoreOptionsConfigItem(val iconResourceId: Int) : ConfigItemType {
        override val configType: Int
            get() = AnalogComplicationConfigRecyclerViewAdapter.TYPE_MORE_OPTIONS
    }

    /**
     * Currently support for the "watch style" configuration. Eventually will
     * morph into general-purpose support for text buttons.
     */
    class WatchfaceChangeStyle(val iconResourceId: Int, val name: String) : ConfigItemType {

        override val configType: Int
            get() = AnalogComplicationConfigRecyclerViewAdapter.TYPE_CHANGE_WATCHFACE_STYLE
    }

    /**
     * Data for a toggle switch (currently unused, but maybe some day)
     */
    class ToggleConfigItem(
            val iconResourceId: Int,
            val displayText: String,
            var enabled: Boolean,
            val callback: (Boolean) -> Unit) : ConfigItemType {


        override val configType: Int
            get() = AnalogComplicationConfigRecyclerViewAdapter.TYPE_TOGGLE
    }
}
