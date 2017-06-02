package org.dwallach.complications

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.support.v7.widget.RecyclerView
import android.support.wearable.complications.ComplicationHelperActivity
import android.support.wearable.complications.ComplicationProviderInfo
import android.support.wearable.complications.ProviderInfoRetriever
import android.support.wearable.complications.ProviderInfoRetriever.OnProviderInfoReceivedCallback
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Switch

import java.util.ArrayList
import java.util.concurrent.Executors

import org.dwallach.R


/**
 * Displays different layouts for configuring watch face's complications and appearance settings
 * (highlight color [second arm], unread notifications, etc.).

 *
 * All appearance settings are saved via [SharedPreferences].

 *
 * Layouts provided by this adapter are split into 5 main view types.

 *
 * A watch face preview including complications. Allows user to tap on the complications to
 * change the complication data and see a live preview of the watch face.

 *
 * Simple arrow to indicate there are more options below the fold.

 *
 * Toggle for unread notifications.

 */
class AnalogComplicationConfigRecyclerViewAdapter(
        private val mContext: Context,
        watchFaceServiceClass: Class<*>,
        private val mSettingsDataSet: ArrayList<AnalogComplicationConfigData.ConfigItemType>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /**
     * Used by associated watch face to let this
     * adapter know which complication locations are supported, their ids, and supported
     * complication data types.
     */
    enum class ComplicationLocation {
        BACKGROUND,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }

    // ComponentName associated with watch face service (service that renders watch face). Used
    // to retrieve complication information.
    private val mWatchFaceComponentName = ComponentName(mContext, watchFaceServiceClass)

    internal var mSharedPref: SharedPreferences

    // Selected complication id by user.
    private var mSelectedComplicationId: Int = 0

    private val mLeftComplicationId: Int
    private val mRightComplicationId: Int

    // Required to retrieve complication data from watch face for preview.
    private val mProviderInfoRetriever: ProviderInfoRetriever

    // Maintains reference view holder to dynamically update watch face preview. Used instead of
    // notifyItemChanged(int position) to avoid flicker and re-inflating the view.
    private var mPreviewAndComplicationsViewHolder: PreviewAndComplicationsViewHolder? = null

    init {

        // Default value is invalid (only changed when user taps to change complication).
        mSelectedComplicationId = -1

        mLeftComplicationId = ComplicationWrapper.getComplicationId(ComplicationLocation.LEFT)
        mRightComplicationId = ComplicationWrapper.getComplicationId(ComplicationLocation.RIGHT)

        mSharedPref = mContext.getSharedPreferences(
                mContext.getString(R.string.analog_complication_preference_file_key),
                Context.MODE_PRIVATE)

        // Initialization of code to retrieve active complication data for the watch face.
        mProviderInfoRetriever = ProviderInfoRetriever(mContext, Executors.newCachedThreadPool())
        mProviderInfoRetriever.init()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        Log.d(TAG, "onCreateViewHolder(): viewType: " + viewType)

        return when (viewType) {
            TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG -> {
                // Need direct reference to watch face preview view holder to update watch face
                // preview based on selections from the user.
                val tmp = PreviewAndComplicationsViewHolder(
                        LayoutInflater.from(parent.context)
                                .inflate(
                                        R.layout.config_list_preview_and_complications_item,
                                        parent,
                                        false))
                mPreviewAndComplicationsViewHolder = tmp
                return tmp
            }

            TYPE_MORE_OPTIONS -> MoreOptionsViewHolder(
                    LayoutInflater.from(parent.context)
                            .inflate(
                                    R.layout.config_list_more_options_item,
                                    parent,
                                    false))

            TYPE_UNREAD_NOTIFICATION_CONFIG -> UnreadNotificationViewHolder(
                    LayoutInflater.from(parent.context)
                            .inflate(
                                    R.layout.config_list_unread_notif_item,
                                    parent,
                                    false))

            else -> throw RuntimeException("unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        Log.d(TAG, "Element $position set.")

        // Pulls all data required for creating the UX for the specific setting option.
        val configItemType = mSettingsDataSet[position]

        when (viewHolder.itemViewType) {
            TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG -> {
                val previewAndComplicationsViewHolder = viewHolder as PreviewAndComplicationsViewHolder

                val previewAndComplicationsConfigItem = configItemType as AnalogComplicationConfigData.PreviewAndComplicationsConfigItem

                val defaultComplicationResourceId = previewAndComplicationsConfigItem.defaultComplicationResourceId
                previewAndComplicationsViewHolder.setDefaultComplicationDrawable(
                        defaultComplicationResourceId)

                previewAndComplicationsViewHolder.initializesColorsAndComplications()
            }

            TYPE_MORE_OPTIONS -> {
                val moreOptionsViewHolder = viewHolder as MoreOptionsViewHolder
                val moreOptionsConfigItem = configItemType as AnalogComplicationConfigData.MoreOptionsConfigItem

                moreOptionsViewHolder.setIcon(moreOptionsConfigItem.iconResourceId)
            }

            TYPE_UNREAD_NOTIFICATION_CONFIG -> {
                val unreadViewHolder = viewHolder as UnreadNotificationViewHolder

                val unreadConfigItem = configItemType as AnalogComplicationConfigData.UnreadNotificationConfigItem

                val unreadEnabledIconResourceId = unreadConfigItem.iconEnabledResourceId
                val unreadDisabledIconResourceId = unreadConfigItem.iconDisabledResourceId

                val unreadName = unreadConfigItem.name
                val unreadSharedPrefId = unreadConfigItem.sharedPrefId

                unreadViewHolder.setIcons(
                        unreadEnabledIconResourceId, unreadDisabledIconResourceId)
                unreadViewHolder.setName(unreadName)
                unreadViewHolder.setSharedPrefId(unreadSharedPrefId)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val configItemType = mSettingsDataSet[position]
        return configItemType.configType
    }

    override fun getItemCount(): Int {
        return mSettingsDataSet.size
    }

    /** Updates the selected complication id saved earlier with the new information.  */
    fun updateSelectedComplication(complicationProviderInfo: ComplicationProviderInfo) {

        Log.d(TAG, "updateSelectedComplication: " + mPreviewAndComplicationsViewHolder)

        // Checks if view is inflated and complication id is valid.
        if (mSelectedComplicationId >= 0) {
            mPreviewAndComplicationsViewHolder?.updateComplicationViews(
                    mSelectedComplicationId, complicationProviderInfo)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView?) {
        super.onDetachedFromRecyclerView(recyclerView)
        // Required to release retriever for active complication data on detach.
        mProviderInfoRetriever.release()
    }

    /**
     * Displays watch face preview along with complication locations. Allows user to tap on the
     * complication they want to change and preview updates dynamically.
     */
    inner class PreviewAndComplicationsViewHolder(view: View) : RecyclerView.ViewHolder(view), OnClickListener {
        private val mWatchFaceArmsAndTicksView = view.findViewById(R.id.watch_face_arms_and_ticks)
        private val mWatchFaceHighlightPreviewView = view.findViewById(R.id.watch_face_highlight)

        private val mLeftComplicationBackground = view.findViewById(R.id.left_complication_background) as ImageView
        private val mRightComplicationBackground = view.findViewById(R.id.right_complication_background) as ImageView

        private val mLeftComplication = view.findViewById(R.id.left_complication) as ImageButton
        private val mRightComplication = view.findViewById(R.id.right_complication) as ImageButton

        private var mDefaultComplicationDrawable: Drawable? = null

        init {
            // Sets up left complication preview.
            mLeftComplication.setOnClickListener(this)
            mRightComplication.setOnClickListener(this)
        }

        override fun onClick(view: View) {
            if (view == mLeftComplication) {
                Log.d(TAG, "Left Complication click()")

                val currentActivity = view.context as Activity
                launchComplicationHelperActivity(currentActivity, ComplicationLocation.LEFT)

            } else if (view == mRightComplication) {
                Log.d(TAG, "Right Complication click()")

                val currentActivity = view.context as Activity
                launchComplicationHelperActivity(currentActivity, ComplicationLocation.RIGHT)
            }
        }

        // Verifies the watch face supports the complication location, then launches the helper
        // class, so user can choose their complication data provider.
        private fun launchComplicationHelperActivity(
                currentActivity: Activity, complicationLocation: ComplicationLocation) {

            mSelectedComplicationId = ComplicationWrapper.getComplicationId(complicationLocation)

            if (mSelectedComplicationId >= 0) {

                val supportedTypes = ComplicationWrapper.getSupportedComplicationTypes(
                        complicationLocation)

                val watchFace = ComponentName(
                        currentActivity, ComplicationWrapper.watchFace::class.java)

                currentActivity.startActivityForResult(
                        ComplicationHelperActivity.createProviderChooserHelperIntent(
                                currentActivity,
                                watchFace,
                                mSelectedComplicationId,
                                *supportedTypes),
                        AnalogComplicationConfigActivity.COMPLICATION_CONFIG_REQUEST_CODE)

            } else {
                Log.d(TAG, "Complication not supported by watch face.")
            }
        }

        fun setDefaultComplicationDrawable(resourceId: Int) {
            val context = mWatchFaceArmsAndTicksView.context
            mDefaultComplicationDrawable = context.getDrawable(resourceId)

            mLeftComplication.setImageDrawable(mDefaultComplicationDrawable)
            mLeftComplicationBackground.visibility = View.INVISIBLE

            mRightComplication.setImageDrawable(mDefaultComplicationDrawable)
            mRightComplicationBackground.visibility = View.INVISIBLE
        }

        fun updateComplicationViews(
                watchFaceComplicationId: Int, complicationProviderInfo: ComplicationProviderInfo?) {
            Log.d(TAG, "updateComplicationViews(): id: " + watchFaceComplicationId)
            Log.d(TAG, "\tinfo: " + complicationProviderInfo)

            if (watchFaceComplicationId == mLeftComplicationId) {
                if (complicationProviderInfo != null) {
                    mLeftComplication.setImageIcon(complicationProviderInfo.providerIcon)
                    mLeftComplicationBackground.visibility = View.VISIBLE

                } else {
                    mLeftComplication.setImageDrawable(mDefaultComplicationDrawable)
                    mLeftComplicationBackground.visibility = View.INVISIBLE
                }

            } else if (watchFaceComplicationId == mRightComplicationId) {
                if (complicationProviderInfo != null) {
                    mRightComplication.setImageIcon(complicationProviderInfo.providerIcon)
                    mRightComplicationBackground.visibility = View.VISIBLE

                } else {
                    mRightComplication.setImageDrawable(mDefaultComplicationDrawable)
                    mRightComplicationBackground.visibility = View.INVISIBLE
                }
            }
        }

        fun initializesColorsAndComplications() {
            val complicationIds = ComplicationWrapper.getComplicationIds()

            mProviderInfoRetriever.retrieveProviderInfo(
                    object : OnProviderInfoReceivedCallback() {
                        override fun onProviderInfoReceived(
                                watchFaceComplicationId: Int,
                                complicationProviderInfo: ComplicationProviderInfo?) {

                            Log.d(TAG, "onProviderInfoReceived: " + complicationProviderInfo)

                            updateComplicationViews(
                                    watchFaceComplicationId, complicationProviderInfo)
                        }
                    },
                    mWatchFaceComponentName,
                    *complicationIds)
        }
    }

    /** Displays icon to indicate there are more options below the fold.  */
    inner class MoreOptionsViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val mMoreOptionsImageView = view.findViewById(R.id.more_options_image_view) as ImageView

        fun setIcon(resourceId: Int) {
            val context = mMoreOptionsImageView.context
            mMoreOptionsImageView.setImageDrawable(context.getDrawable(resourceId))
        }
    }

    /**
     * Displays switch to indicate whether or not icon appears for unread notifications. User can
     * toggle on/off.
     */
    inner class UnreadNotificationViewHolder(view: View) : RecyclerView.ViewHolder(view), OnClickListener {

        private var mUnreadNotificationSwitch = view.findViewById(R.id.unread_notification_switch) as Switch

        private var mEnabledIconResourceId: Int = 0
        private var mDisabledIconResourceId: Int = 0

        private var mSharedPrefResourceId: Int = 0

        init {
            view.setOnClickListener(this)
        }

        fun setName(name: String) {
            mUnreadNotificationSwitch.text = name
        }

        fun setIcons(enabledIconResourceId: Int, disabledIconResourceId: Int) {

            mEnabledIconResourceId = enabledIconResourceId
            mDisabledIconResourceId = disabledIconResourceId

            val context = mUnreadNotificationSwitch.context

            // Set default to enabled.
            mUnreadNotificationSwitch.setCompoundDrawablesWithIntrinsicBounds(
                    context.getDrawable(mEnabledIconResourceId), null, null, null)
        }

        fun setSharedPrefId(sharedPrefId: Int) {
            mSharedPrefResourceId = sharedPrefId

            val context = mUnreadNotificationSwitch.context
            val sharedPreferenceString = context.getString(mSharedPrefResourceId)
            val currentState = mSharedPref.getBoolean(sharedPreferenceString, true)

            updateIcon(context, currentState)
        }

        private fun updateIcon(context: Context, currentState: Boolean) {
            val currentIconResourceId: Int

            if (currentState) {
                currentIconResourceId = mEnabledIconResourceId
            } else {
                currentIconResourceId = mDisabledIconResourceId
            }

            mUnreadNotificationSwitch.isChecked = currentState
            mUnreadNotificationSwitch.setCompoundDrawablesWithIntrinsicBounds(
                    context.getDrawable(currentIconResourceId), null, null, null)
        }

        override fun onClick(view: View) {
            val position = adapterPosition
            Log.d(TAG, "Complication onClick() position: " + position)

            val context = view.context
            val sharedPreferenceString = context.getString(mSharedPrefResourceId)

            // Since user clicked on a switch, new state should be opposite of current state.
            val newState = !mSharedPref.getBoolean(sharedPreferenceString, true)

            val editor = mSharedPref.edit()
            editor.putBoolean(sharedPreferenceString, newState)
            editor.apply()

            updateIcon(context, newState)
        }
    }

    companion object {

        private val TAG = "CompConfigAdapter"

        val TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG = 0
        val TYPE_MORE_OPTIONS = 1
        val TYPE_UNREAD_NOTIFICATION_CONFIG = 3
    }
}
