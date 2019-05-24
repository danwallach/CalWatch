/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2019 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.complications

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
// import android.support.v7.widget.RecyclerView
import android.support.wearable.complications.ComplicationHelperActivity
import android.support.wearable.complications.ComplicationProviderInfo
import android.support.wearable.complications.ProviderInfoRetriever
import android.support.wearable.complications.ProviderInfoRetriever.OnProviderInfoReceivedCallback
import android.support.wearable.watchface.CanvasWatchFaceService
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Switch
import androidx.recyclerview.widget.RecyclerView

import java.util.concurrent.Executors

import org.dwallach.R
import org.dwallach.calwatch2.ClockState
import org.dwallach.calwatch2.PreferencesHelper
import org.dwallach.calwatch2.Utilities
import org.dwallach.calwatch2.errorLogAndThrow
import org.dwallach.complications.AnalogComplicationConfigData.ConfigItemType
import org.dwallach.complications.ComplicationLocation.BOTTOM
import org.dwallach.complications.ComplicationLocation.LEFT
import org.dwallach.complications.ComplicationLocation.RIGHT
import org.dwallach.complications.ComplicationLocation.TOP
import org.dwallach.complications.ComplicationWrapper.BOTTOM_COMPLICATION_ID
import org.dwallach.complications.ComplicationWrapper.LEFT_COMPLICATION_ID
import org.dwallach.complications.ComplicationWrapper.RIGHT_COMPLICATION_ID
import org.dwallach.complications.ComplicationWrapper.TOP_COMPLICATION_ID
import org.dwallach.complications.ComplicationWrapper.getComplicationId
import org.dwallach.complications.ComplicationWrapper.getSupportedComplicationTypes
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.info
import org.jetbrains.anko.verbose
import org.jetbrains.anko.warn
import java.util.WeakHashMap

/**
 * This class handles all the different config items that might ever be displayed.
 * The WearableRecyclerView is going to query this.
 *
 * Each inner class corresponds to one of the entry types that might be returned.
 * There's a little bit of planning for the future features here; we can add
 * additional toggle switches via [ToggleViewHolder] without needing to redo
 * everything else.
 *
 * See also: [AnalogComplicationConfigData], which deals with WearOS 1 vs. 2.
 * (We don't want to display the complications selector when the watch doesn't
 * support complications.)
 */
class AnalogComplicationConfigRecyclerViewAdapter(
    mContext: Context,
    watchFaceServiceClass: Class<out CanvasWatchFaceService>,
    private val mSettingsDataSet: List<ConfigItemType>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), AnkoLogger {

    // mapping from configTypes (int) to ConfigItemType
    private val mSettingsTypeMap = mSettingsDataSet.associateBy { it.configType }

    // Useful reading on all this WearableRecylerView nonsense:
    // http://www.technotalkative.com/android-wear-part-5-wearablelistview/

    // ComponentName associated with watch face service (service that renders watch face). Used
    // to retrieve complication information.
    private val mWatchFaceComponentName = ComponentName(mContext, watchFaceServiceClass)

    // Selected complication id by user.
    private var mSelectedComplicationId: Int = 0

    // Required to retrieve complication data from watch face for preview.
    private val mProviderInfoRetriever: ProviderInfoRetriever

    // Maintains reference view holder to dynamically update watch face preview. Used instead of
    // notifyItemChanged(int position) to avoid flicker and re-inflating the view.
    private var mPreviewAndComplicationsViewHolder: PreviewAndComplicationsViewHolder? = null

    init {
        // Default value is invalid (only changed when user taps to change complication).
        mSelectedComplicationId = -1

        // Initialization of code to retrieve active complication data for the watch face.
        mProviderInfoRetriever = ProviderInfoRetriever(mContext, Executors.newCachedThreadPool())
        mProviderInfoRetriever.init()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        verbose { "onCreateViewHolder(): viewType: $viewType" }

        val settings = mSettingsTypeMap[viewType] ?: errorLogAndThrow("No settings for viewType: $viewType")

        return when (viewType) {
            TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG -> {
                // Need direct reference to watch face preview view holder to update watch face
                // preview based on selections from the user.
                val tmp = PreviewAndComplicationsViewHolder(
                    settings.iconResourceId,
                    LayoutInflater.from(parent.context)
                        .inflate(
                            settings.layoutId,
                            parent,
                            false
                        )
                )
                mPreviewAndComplicationsViewHolder = tmp
                return tmp
            }

            TYPE_MORE_OPTIONS -> MoreOptionsViewHolder(
                settings.iconResourceId,
                LayoutInflater.from(parent.context)
                    .inflate(settings.layoutId, parent, false)
            )

            TYPE_CHANGE_WATCHFACE_STYLE ->
                VanillaViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(settings.layoutId, parent, false)
                )

            TYPE_DAY_DATE ->
                ToggleViewHolder(settings.inflatedId,
                    settings.iconResourceId,
                    settings.name,
                    LayoutInflater.from(parent.context)
                        .inflate(settings.layoutId, parent, false),
                    { ClockState.showDayDate }, {
                        ClockState.showDayDate = it
                        PreferencesHelper.savePreferences(parent.context)
                        Utilities.redrawEverything()
                    })

            else -> throw RuntimeException("unknown viewType: $viewType")
        }
    }

    /** All our "holders" also have a bindInit() method to set them up */
    interface HolderInit {
        fun bindInit()
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        verbose { "Element $position set." }

        (viewHolder as HolderInit).bindInit()
    }

    override fun getItemViewType(position: Int): Int {
        val configItemType = mSettingsDataSet[position]
        return configItemType.configType
    }

    override fun getItemCount(): Int {
        return mSettingsDataSet.size
    }

    /**
     * Updates the selected complication id saved earlier with the new information. null if
     * the complication has been set to be "empty".
     */
    fun updateSelectedComplication(complicationProviderInfo: ComplicationProviderInfo?) {

        info { "updateSelectedComplication: $mPreviewAndComplicationsViewHolder" }

        // Checks if view is inflated and complication id is valid.
        if (mSelectedComplicationId >= 0) {
            mPreviewAndComplicationsViewHolder?.updateComplicationViews(
                mSelectedComplicationId, complicationProviderInfo
            )
        }
    }

    /**
     * Displays watch face preview along with complication locations. Allows user to tap on the
     * complication they want to change and preview updates dynamically.
     */
    inner class PreviewAndComplicationsViewHolder(private val iconId: Int, view: View) : RecyclerView.ViewHolder(view),
        OnClickListener, HolderInit {
        private val mWatchFaceArmsAndTicksView = view.findViewById<View>(R.id.watch_face_arms_and_ticks)

        private val mLeftComplicationBackground = view.findViewById<ImageView>(R.id.left_complication_background)
        private val mRightComplicationBackground = view.findViewById<ImageView>(R.id.right_complication_background)
        private val mTopComplicationBackground = view.findViewById<ImageView>(R.id.top_complication_background)
        private val mBottomComplicationBackground = view.findViewById<ImageView>(R.id.bottom_complication_background)

        private val mLeftComplication = view.findViewById<ImageButton>(R.id.left_complication)
        private val mRightComplication = view.findViewById<ImageButton>(R.id.right_complication)
        private val mTopComplication = view.findViewById<ImageButton>(R.id.top_complication)
        private val mBottomComplication = view.findViewById<ImageButton>(R.id.bottom_complication)

        override fun bindInit() {
            setDefaultComplicationDrawable()
            initializesColorsAndComplications()
        }

        private fun idToComplicationFn(id: Int) = when (id) {
            LEFT_COMPLICATION_ID -> mLeftComplication
            RIGHT_COMPLICATION_ID -> mRightComplication
            TOP_COMPLICATION_ID -> mTopComplication
            BOTTOM_COMPLICATION_ID -> mBottomComplication
            else -> null
        }

        private fun idToComplicationBackgroundFn(id: Int) = when (id) {
            LEFT_COMPLICATION_ID -> mLeftComplicationBackground
            RIGHT_COMPLICATION_ID -> mRightComplicationBackground
            TOP_COMPLICATION_ID -> mTopComplicationBackground
            BOTTOM_COMPLICATION_ID -> mBottomComplicationBackground
            else -> null
        }

        private val idToComplication =
            ComplicationWrapper.activeComplicationIds.associate { it to idToComplicationFn(it) }
        private val idToComplicationBackground =
            ComplicationWrapper.activeComplicationIds.associate { it to idToComplicationBackgroundFn(it) }
        private val disabledComplicationIds =
            ComplicationWrapper.inactiveComplicationIds.toSet()
        private val disabledComplications =
            disabledComplicationIds.map(this::idToComplicationFn)
        private val disabledComplicationBackgrounds =
            disabledComplicationIds.map(this::idToComplicationBackgroundFn)

        private lateinit var mDefaultComplicationDrawable: Drawable

        init {
            idToComplication.values.forEach { it?.setOnClickListener(this) }

            info { "Complications from wrapper: ${ComplicationWrapper.complicationIds.toSet()}" }
            info { "Active complications from wrapper: ${ComplicationWrapper.activeComplicationIds.toSet()}" }
            info { "Total num complications (idToComplication): ${idToComplication.size}" }
            info { "Disable complication Ids: $disabledComplicationIds" }
            info { "Num disabled: ${disabledComplications.size}" }
            info { "Num disabled bg: ${disabledComplicationBackgrounds.size}" }
        }

        override fun onClick(view: View) {
            val location = when (view) {
                mLeftComplication -> LEFT
                mRightComplication -> RIGHT
                mBottomComplication -> BOTTOM
                mTopComplication -> TOP
                else -> null
            }
            val currentActivity = view.context as Activity

            if (location != null)
                launchComplicationHelperActivity(currentActivity, location)
            else
                warn { "Couldn't figure out location for click! Ignoring." }
        }

        // Verifies the watch face supports the complication location, then launches the helper
        // class, so user can choose their complication data provider.
        private fun launchComplicationHelperActivity(
            currentActivity: Activity,
            complicationLocation: ComplicationLocation
        ) {

            mSelectedComplicationId = getComplicationId(complicationLocation)

            if (mSelectedComplicationId >= 0) {

                val supportedTypes = getSupportedComplicationTypes(complicationLocation)
                val watchFace = ComponentName(currentActivity, ComplicationWrapper.watchFaceClass)

                currentActivity.startActivityForResult(
                    ComplicationHelperActivity.createProviderChooserHelperIntent(
                        currentActivity,
                        watchFace,
                        mSelectedComplicationId,
                        *supportedTypes
                    ),
                    AnalogComplicationConfigActivity.COMPLICATION_CONFIG_REQUEST_CODE
                )
            } else {
                warn { "Complication not supported by watch face." }
            }
        }

        private fun setDefaultComplicationDrawable() {
            info { "setting default complication drawable, resourceId = $iconId" }

            // This is a bit of a hack, but it works.
            val context = mWatchFaceArmsAndTicksView.context ?: return

            // It's technically possible or getDrawable to return null, but we're looking
            // up an id that was just passed to us, so it will never fail in practice.
            mDefaultComplicationDrawable = context.getDrawable(iconId) ?: return

            idToComplication.values.forEach {
                it?.setImageDrawable(mDefaultComplicationDrawable)
            }

            disabledComplications.forEach {
                it?.visibility = View.INVISIBLE
            }

//            idToComplicationBackground.values.forEach {
//                it?.visibility = View.INVISIBLE
//            }

            disabledComplicationBackgrounds.forEach {
                it?.visibility = View.INVISIBLE
            }
        }

        fun updateComplicationViews(
            watchFaceComplicationId: Int,
            complicationProviderInfo: ComplicationProviderInfo?
        ) {
            info { "updateComplicationViews(): id:  $watchFaceComplicationId" }
            info { "\tinfo: $complicationProviderInfo" }

            val complication = idToComplication[watchFaceComplicationId]
            val complicationBg = idToComplicationBackground[watchFaceComplicationId]

            if (complication != null && complicationBg != null) {
                if (complicationProviderInfo != null) {
                    complication.setImageIcon(complicationProviderInfo.providerIcon)
                    complicationBg.visibility = View.VISIBLE
                } else {
                    complication.setImageDrawable(mDefaultComplicationDrawable)
                    complicationBg.visibility = View.INVISIBLE
                }
            } else {
                warn {
                    "Couldn't find complication (%s) or complicationBg (%s) for id $watchFaceComplicationId".format(
                        if (complication == null) "null" else "okay",
                        if (complicationBg == null) "null" else "okay"
                    )
                }
            }
        }

        private fun initializesColorsAndComplications() {
            val complicationIds = ComplicationWrapper.complicationIds

            mProviderInfoRetriever.retrieveProviderInfo(
                object : OnProviderInfoReceivedCallback() {
                    override fun onProviderInfoReceived(
                        watchFaceComplicationId: Int,
                        complicationProviderInfo: ComplicationProviderInfo?
                    ) {

                        debug { "onProviderInfoReceived: $complicationProviderInfo" }

                        updateComplicationViews(watchFaceComplicationId, complicationProviderInfo)
                    }
                },
                mWatchFaceComponentName,
                *complicationIds
            )
        }
    }

    /** Displays icon to indicate there are more options below the fold.  */
    inner class MoreOptionsViewHolder(private val iconId: Int, view: View) : RecyclerView.ViewHolder(view), HolderInit {

        private val mMoreOptionsImageView = view.findViewById<ImageView>(R.id.more_options_image_view)

        override fun bindInit() {
            val context = mMoreOptionsImageView.context
            if (context == null)
                warn("No context for MoreOptionsViewHolder")
            else
                mMoreOptionsImageView.setImageDrawable(context.getDrawable(iconId))
        }
    }

    /** Nothing much going on here, but it's still necessary. */
    inner class VanillaViewHolder(view: View) : RecyclerView.ViewHolder(view), HolderInit {
        override fun bindInit() {}
    }

    inner class ToggleViewHolder(
        viewId: Int,
        @Suppress("UNUSED_PARAMETER") iconId: Int,
        private val text: String,
        view: View,
        val isSelected: () -> Boolean,
        val callback: (Boolean) -> Unit
    ) :
        RecyclerView.ViewHolder(view), HolderInit, OnClickListener {

        private val switch = view.findViewById<Switch>(viewId)

        init {
            allToggles[this] = true
        }

        fun reloadState() {
            switch.isChecked = isSelected()
        }

        override fun bindInit() {
            reloadState()
            switch.setOnClickListener(this)

            // TODO: the line below sets up the icon on the left, currently disabled, but what
            //   we really want is to move the toggle-switch to the left side, which doesn't seem
            //   to be easily done.

//            switch.setCompoundDrawablesWithIntrinsicBounds(switch.context.getDrawable(iconId), null, null, null)
        }

        override fun onClick(v: View?) {
            val switchState = switch.isChecked
            info { "Switch ($text) checked: $switchState" }

            callback(switch.isChecked)
        }
    }

    companion object {
        const val TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG = 0
        const val TYPE_MORE_OPTIONS = 1
        const val TYPE_DAY_DATE = 2
        const val TYPE_CHANGE_WATCHFACE_STYLE = 3

        private val allToggles = WeakHashMap<ToggleViewHolder, Boolean>()

        fun reloadAllToggles() = allToggles.keys.forEach { it.reloadState() }
    }
}
