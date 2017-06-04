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
import android.support.wearable.watchface.CanvasWatchFaceService
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView

import java.util.concurrent.Executors

import org.dwallach.R
import org.dwallach.complications.AnalogComplicationConfigData.BackgroundComplicationConfigItem
import org.dwallach.complications.AnalogComplicationConfigData.ConfigItemType
import org.dwallach.complications.AnalogComplicationConfigData.MoreOptionsConfigItem
import org.dwallach.complications.AnalogComplicationConfigData.PreviewAndComplicationsConfigItem
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.verbose

/**
 * Displays different layouts for configuring watch face's complications and appearance settings
 *
 * All appearance settings are saved via [SharedPreferences].

 *
 * Layouts provided by this adapter are split into 5 main view types.

 *
 * A watch face preview including complications. Allows user to tap on the complications to
 * change the complication data and see a live preview of the watch face.

 *
 * Simple arrow to indicate there are more options below the fold.

 */
class AnalogComplicationConfigRecyclerViewAdapter(
        private val mContext: Context,
        watchFaceServiceClass: Class<out CanvasWatchFaceService>,
        private val mSettingsDataSet: List<ConfigItemType>) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), AnkoLogger {

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

    private val mLeftComplicationId = ComplicationWrapper.getComplicationId(ComplicationLocation.LEFT)
    private val mRightComplicationId = ComplicationWrapper.getComplicationId(ComplicationLocation.RIGHT)
    private val mBackgroundComplicationId = ComplicationWrapper.getComplicationId(ComplicationLocation.BACKGROUND)

    // Required to retrieve complication data from watch face for preview.
    private val mProviderInfoRetriever: ProviderInfoRetriever

    // Maintains reference view holder to dynamically update watch face preview. Used instead of
    // notifyItemChanged(int position) to avoid flicker and re-inflating the view.
    private var mPreviewAndComplicationsViewHolder: PreviewAndComplicationsViewHolder? = null

    init {

        // Default value is invalid (only changed when user taps to change complication).
        mSelectedComplicationId = -1

        mSharedPref = mContext.getSharedPreferences(
                mContext.getString(R.string.analog_complication_preference_file_key),
                Context.MODE_PRIVATE)

        // Initialization of code to retrieve active complication data for the watch face.
        mProviderInfoRetriever = ProviderInfoRetriever(mContext, Executors.newCachedThreadPool())
        mProviderInfoRetriever.init()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        verbose { "onCreateViewHolder(): viewType: $viewType" }

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

            TYPE_BACKGROUND_COMPLICATION_IMAGE_CONFIG ->
                    BackgroundComplicationViewHolder(LayoutInflater.from(parent.context)
                            .inflate(
                                    R.layout.config_list_background_complication_item,
                                    parent,
                                    false))

            else -> throw RuntimeException("unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        verbose { "Element $position set." }

        // Pulls all data required for creating the UX for the specific setting option.
        val configItemType = mSettingsDataSet[position]

        when (viewHolder.itemViewType) {
            TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG -> {
                val previewAndComplicationsViewHolder = viewHolder as PreviewAndComplicationsViewHolder

                val previewAndComplicationsConfigItem = configItemType as PreviewAndComplicationsConfigItem

                val defaultComplicationResourceId = previewAndComplicationsConfigItem.defaultComplicationResourceId
                previewAndComplicationsViewHolder.setDefaultComplicationDrawable(
                        defaultComplicationResourceId)

                previewAndComplicationsViewHolder.initializesColorsAndComplications()
            }

            TYPE_MORE_OPTIONS -> {
                val moreOptionsViewHolder = viewHolder as MoreOptionsViewHolder
                val moreOptionsConfigItem = configItemType as MoreOptionsConfigItem

                moreOptionsViewHolder.setIcon(moreOptionsConfigItem.iconResourceId)
            }

            TYPE_BACKGROUND_COMPLICATION_IMAGE_CONFIG -> {
                val backgroundComplicationViewHolder = viewHolder as BackgroundComplicationViewHolder

                val backgroundComplicationConfigItem = configItemType as BackgroundComplicationConfigItem

                val backgroundIconResourceId = backgroundComplicationConfigItem.iconResourceId
                val backgroundName = backgroundComplicationConfigItem.name

                backgroundComplicationViewHolder.setIcon(backgroundIconResourceId)
                backgroundComplicationViewHolder.setName(backgroundName)
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

        verbose { "updateSelectedComplication: $mPreviewAndComplicationsViewHolder" }

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
        private val mWatchFaceArmsAndTicksView = view.findViewById<View>(R.id.watch_face_arms_and_ticks)
        private val mWatchFaceHighlightPreviewView = view.findViewById<View>(R.id.watch_face_highlight)

        private val mLeftComplicationBackground = view.findViewById<ImageView>(R.id.left_complication_background)
        private val mRightComplicationBackground = view.findViewById<ImageView>(R.id.right_complication_background)

        private val mLeftComplication = view.findViewById<ImageButton>(R.id.left_complication)
        private val mRightComplication = view.findViewById<ImageButton>(R.id.right_complication)

        private var mDefaultComplicationDrawable: Drawable? = null

        init {
            // Sets up left complication preview.
            mLeftComplication.setOnClickListener(this)
            mRightComplication.setOnClickListener(this)
        }

        override fun onClick(view: View) {
            if (view == mLeftComplication) {
                verbose { "Left Complication click()" }

                val currentActivity = view.context as Activity
                launchComplicationHelperActivity(currentActivity, ComplicationLocation.LEFT)

            } else if (view == mRightComplication) {
                verbose { "Right Complication click()" }

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
                verbose { "Complication not supported by watch face." }
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

        fun updateComplicationViews(watchFaceComplicationId: Int,
                                    complicationProviderInfo: ComplicationProviderInfo?) {
            verbose { "updateComplicationViews(): id:  $watchFaceComplicationId" }
            verbose { "\tinfo: $complicationProviderInfo" }

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

                            debug { "onProviderInfoReceived: $complicationProviderInfo" }

                            updateComplicationViews(watchFaceComplicationId, complicationProviderInfo)
                        }
                    },
                    mWatchFaceComponentName,
                    *complicationIds)
        }
    }

    /** Displays icon to indicate there are more options below the fold.  */
    inner class MoreOptionsViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val mMoreOptionsImageView = view.findViewById<ImageView>(R.id.more_options_image_view)

        fun setIcon(resourceId: Int) {
            val context = mMoreOptionsImageView.context
            mMoreOptionsImageView.setImageDrawable(context.getDrawable(resourceId))
        }
    }

    /** Displays button to trigger background image complication selector.  */
    inner class BackgroundComplicationViewHolder(view: View) : RecyclerView.ViewHolder(view), OnClickListener {
        private val mBackgroundComplicationButton = view.findViewById<Button>(R.id.background_complication_button)

        init {
            view.setOnClickListener(this)
        }

        fun setName(name: String) {
            mBackgroundComplicationButton.text = name
        }

        fun setIcon(resourceId: Int) {
            val context = mBackgroundComplicationButton.context
            mBackgroundComplicationButton.setCompoundDrawablesWithIntrinsicBounds(
                    context.getDrawable(resourceId), null, null, null)
        }

        override fun onClick(view: View) {
            val position = adapterPosition
            debug { "Background Complication onClick() position: $position" }

            val currentActivity = view.context as Activity

            mSelectedComplicationId = ComplicationWrapper.getComplicationId(
                    ComplicationLocation.BACKGROUND)

            if (mSelectedComplicationId >= 0) {

                val supportedTypes = ComplicationWrapper.getSupportedComplicationTypes(
                        ComplicationLocation.BACKGROUND)

                val watchFace = ComponentName(currentActivity, ComplicationWrapper.watchFace::class.java)

                currentActivity.startActivityForResult(
                        ComplicationHelperActivity.createProviderChooserHelperIntent(
                                currentActivity,
                                watchFace,
                                mSelectedComplicationId,
                                *supportedTypes),
                        AnalogComplicationConfigActivity.COMPLICATION_CONFIG_REQUEST_CODE)

            } else {
                debug { "Complication not supported by watch face." }
            }
        }
    }

    companion object {

        private val TAG = "CompConfigAdapter"

        val TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG = 0
        val TYPE_MORE_OPTIONS = 1
        val TYPE_BACKGROUND_COMPLICATION_IMAGE_CONFIG = 2
    }
}
