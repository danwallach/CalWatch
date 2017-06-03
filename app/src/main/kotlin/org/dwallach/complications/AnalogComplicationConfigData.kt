package org.dwallach.complications

import android.content.Context
import android.support.v7.widget.RecyclerView.ViewHolder
import android.support.v7.widget.RecyclerView

import org.dwallach.R

object AnalogComplicationConfigData {
    /**
     * Interface all ConfigItems must implement so the [RecyclerView]'s Adapter associated
     * with the configuration activity knows what type of ViewHolder to inflate.
     */
    interface ConfigItemType {
        val configType: Int
    }

    /**
     * Includes all data to populate each of the 5 different custom
     * [ViewHolder] types in [AnalogComplicationConfigRecyclerViewAdapter].
     */
    fun getDataToPopulateAdapter(context: Context): List<ConfigItemType> =
            listOf(PreviewAndComplicationsConfigItem(R.drawable.add_complication),
                    MoreOptionsConfigItem(R.drawable.ic_expand_more_white_18dp),
                    BackgroundComplicationConfigItem(
                            context.getString(R.string.config_background_image_complication_label),
                            R.drawable.ic_landscape_white))

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
            get() = AnalogComplicationConfigRecyclerViewAdapter.TYPE_BACKGROUND_COMPLICATION_IMAGE_CONFIG
    }
}
