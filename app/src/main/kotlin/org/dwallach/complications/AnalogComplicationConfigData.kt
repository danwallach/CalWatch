/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2022 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.complications

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import org.dwallach.R

private val TAG = "AnalogComplicationConfigDAta"

/**
 * Code support to interact with the [RecyclerView]. Note that this code
 * differentiates what we're doing based on the Android SDK version. If
 * we're on Wear 1.x, then there's no support for complications, so we'll
 * leave that out from our configuration dialog panel.
 */
object AnalogComplicationConfigData {
    data class ConfigItemType(
        val configType: Int,
        val iconResourceId: Int,
        val layoutId: Int,
        val inflatedId: Int,
        val name: String = "",
        val disabled: Boolean = false
    )

    /**
     * Includes all data to populate each of the different custom
     * [ViewHolder] types in [AnalogComplicationConfigRecyclerViewAdapter].
     */
    fun getDataToPopulateAdapter(context: Context): List<ConfigItemType> =
        if (Build.VERSION.SDK_INT < 24) {
            // Wear 1.x --- no complications so we won't show that part of the config choices
            Log.i(TAG, "getDataToPopulateAdapter: Wear 1.x")
            listOf(
                ConfigItemType(
                    AnalogComplicationConfigRecyclerViewAdapter.TYPE_DAY_DATE,
                    R.drawable.ic_date_time_selector,
                    R.layout.config_day_date_select,
                    R.id.day_date_select,
                    context.getString(R.string.watchface_day_date_selector)
                ),
                ConfigItemType(
                    AnalogComplicationConfigRecyclerViewAdapter.TYPE_CHANGE_WATCHFACE_STYLE,
                    R.drawable.icon_preview, // shouldn't be drawn ever, but...
                    R.layout.config_watchface_style,
                    R.id.watchface_view_style,
                    context.getString(R.string.watchface_style_selector)
                )
            )
        } else {
            Log.i(TAG, "getDataToPopulateAdapter: Wear 2+")
            // Wear 2+
            listOf(
                ConfigItemType(
                    AnalogComplicationConfigRecyclerViewAdapter.TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG,
                    R.drawable.add_complication,
                    R.layout.config_list_preview_and_complications_item,
                    R.id.preview_and_complications_item
                ),
                ConfigItemType(
                    AnalogComplicationConfigRecyclerViewAdapter.TYPE_MORE_OPTIONS,
                    R.drawable.ic_expand_more_white_18dp,
                    R.layout.config_list_more_options_item,
                    R.id.more_options_image_view
                ),
                ConfigItemType(
                    AnalogComplicationConfigRecyclerViewAdapter.TYPE_DAY_DATE,
                    R.drawable.ic_date_time_selector,
                    R.layout.config_day_date_select,
                    R.id.day_date_select,
                    context.getString(R.string.watchface_day_date_selector)
                ),
                ConfigItemType(
                    AnalogComplicationConfigRecyclerViewAdapter.TYPE_CHANGE_WATCHFACE_STYLE,
                    R.drawable.icon_preview, // shouldn't be drawn ever, but...
                    R.layout.config_watchface_style,
                    R.id.watchface_view_style,
                    context.getString(R.string.watchface_style_selector)
                )
            )
        }
}
