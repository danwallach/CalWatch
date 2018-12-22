/*
 * CalWatch Complications Support
 * Copyright (C) 2017 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.complications

import android.content.Context
import androidx.recyclerview.widget.RecyclerView.ViewHolder

import org.dwallach.R
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info

object AnalogComplicationConfigData: AnkoLogger {
    data class ConfigItemType( val configType: Int, val iconResourceId: Int, val name: String = "")

    /**
     * Includes all data to populate each of the different custom
     * [ViewHolder] types in [AnalogComplicationConfigRecyclerViewAdapter].
     */
    fun getDataToPopulateAdapter(context: Context): List<ConfigItemType> {
        info("getDataToPopulateAdapter")

        return listOf(
                ConfigItemType(
                        AnalogComplicationConfigRecyclerViewAdapter.TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG,
                        R.drawable.add_complication),
                ConfigItemType(
                        AnalogComplicationConfigRecyclerViewAdapter.TYPE_MORE_OPTIONS,
                        R.drawable.ic_expand_more_white_18dp),
                ConfigItemType(
                        AnalogComplicationConfigRecyclerViewAdapter.TYPE_CHANGE_WATCHFACE_STYLE,
                        R.drawable.ic_all_faces_icon,
                        context.getString(R.string.watchface_style_selector)))
    }
}
