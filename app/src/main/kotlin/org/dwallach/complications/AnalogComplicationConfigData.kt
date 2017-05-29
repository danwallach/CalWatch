package org.dwallach.complications

import android.content.Context
import android.support.v7.widget.RecyclerView.ViewHolder
import android.support.v7.widget.RecyclerView

import java.util.ArrayList

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
     * Returns Watch Face Service class associated with configuration Activity.
     */
    val watchFaceServiceClass: Class<*>
        get() = ComplicationManager.getWatchFaceService()::class.java

    /**
     * Includes all data to populate each of the 5 different custom
     * [ViewHolder] types in [AnalogComplicationConfigRecyclerViewAdapter].
     */
    fun getDataToPopulateAdapter(context: Context): ArrayList<ConfigItemType> {

        val settingsConfigData = ArrayList<ConfigItemType>()

        // Data for watch face preview and complications UX in settings Activity.
        val complicationConfigItem = PreviewAndComplicationsConfigItem(R.drawable.add_complication)
        settingsConfigData.add(complicationConfigItem)

        // Data for "more options" UX in settings Activity.
        val moreOptionsConfigItem = MoreOptionsConfigItem(R.drawable.ic_expand_more_white_18dp)
        settingsConfigData.add(moreOptionsConfigItem)

        // Data for 'Unread Notifications' UX (toggle) in settings Activity.
        val unreadNotificationsConfigItem = UnreadNotificationConfigItem(
                context.getString(R.string.config_unread_notifications_label),
                R.drawable.ic_notifications_white_24dp,
                R.drawable.ic_notifications_off_white_24dp,
                R.string.saved_unread_notifications_pref)
        settingsConfigData.add(unreadNotificationsConfigItem)

        return settingsConfigData
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
     * Data for Unread Notification preference picker item in RecyclerView.
     */
    class UnreadNotificationConfigItem internal constructor(
            val name: String,
            val iconEnabledResourceId: Int,
            val iconDisabledResourceId: Int,
            val sharedPrefId: Int) : ConfigItemType {

        override val configType: Int
            get() = AnalogComplicationConfigRecyclerViewAdapter.TYPE_UNREAD_NOTIFICATION_CONFIG
    }
}
