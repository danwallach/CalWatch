package org.dwallach.complications

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.support.wearable.watchface.CanvasWatchFaceService
import org.jetbrains.anko.AnkoLogger
import java.lang.ref.WeakReference
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationData.*
import org.dwallach.complications.AnalogComplicationConfigRecyclerViewAdapter.*
import org.dwallach.complications.AnalogComplicationConfigRecyclerViewAdapter.ComplicationLocation.*
import android.support.wearable.complications.rendering.ComplicationDrawable
import org.jetbrains.anko.verbose
import android.support.wearable.complications.ComplicationHelperActivity
import android.content.ComponentName
import org.jetbrains.anko.warn


/**
 * The goal is this is to provide a super minimal interface for two things: feeding in configuration
 * data for menus (the complication selectors will be added in automatically) via [loadMenus],
 * drawing the complications via [drawComplications], etc.
 *
 * Code here began its life in the exame AnalogWatchFaceService but has been largely rewritten
 * to be relatively independent of the watchface itself, to remove color-setting options, and
 * to use Kotlin's nice features when appropriate.
 */
object ComplicationWrapper : AnkoLogger {
    private var watchFaceRef: WeakReference<CanvasWatchFaceService?>? = null

    /**
     * Here's a property to get the CanvasWatchFaceService in use. Set it at startup time.
     * Storage internally is a weak reference, so it's possible, albeit unlikely, that this
     * will fail, causing a RuntimeException. Yeah, that kinda sucks, but if the watchface
     * is active, this won't be an issue. We're using a WeakReference to make sure that
     * we're not accidentally keeping anything live that really should die at cleanup time.
     */
    var watchFace: CanvasWatchFaceService
        get() = watchFaceRef?.get() ?: throw RuntimeException("no watchface service found!")
        set(watchFace) {
            watchFaceRef = WeakReference(watchFace)
            initializeComplicationsAndBackground() // seems as good a place to this as anything
        }

    /**
     * Call this function to load up menu items, along with their callbacks, which you want
     * to be shown as part of the watchface configuration dialog. Each argument is a [MenuGroup]
     * which is to say it could be a [RadioGroup] or [Toggle]. To simplify i18n issues, we're
     * not including strings anywhere here. You just include a drawable icon, and that's it.
     */
    fun loadMenus(vararg menus: MenuGroup) {
        throw NotImplementedError("not implemented yet")
    }

    /**
     * Call this from your main redraw loop and the complications will be rendered to the given canvas.
     */
    fun drawComplications(canvas: Canvas, currentTimeMillis: Long) =
        complicationDrawableMap.keys
                .filter { it != BACKGROUND_COMPLICATION_ID }
                .forEach { complicationDrawableMap[it]?.draw(canvas, currentTimeMillis) }

    /**
     * Call this from your main redraw loop specifically to draw the background complication.
     */
    fun drawBackgroundComplication(canvas: Canvas, currentTimeMillis: Long) =
        complicationDrawableMap[BACKGROUND_COMPLICATION_ID]?.draw(canvas, currentTimeMillis)

    // TODO bridges for ambient mode, screen resolution, etc.

    // Unique IDs for each complication. The settings activity that supports allowing users
    // to select their complication data provider requires numbers to be >= 0.
    private val BACKGROUND_COMPLICATION_ID = 0

    private val LEFT_COMPLICATION_ID = 100
    private val RIGHT_COMPLICATION_ID = 101

    // Background, Left and right complication IDs as array for Complication API.
    private val COMPLICATION_IDS = intArrayOf(BACKGROUND_COMPLICATION_ID, LEFT_COMPLICATION_ID, RIGHT_COMPLICATION_ID)

    // Left and right dial supported types.
    private val COMPLICATION_SUPPORTED_TYPES = arrayOf(
            intArrayOf(TYPE_LARGE_IMAGE),
            intArrayOf(TYPE_RANGED_VALUE, TYPE_ICON, TYPE_SHORT_TEXT, TYPE_SMALL_IMAGE),
            intArrayOf(TYPE_RANGED_VALUE, TYPE_ICON, TYPE_SHORT_TEXT, TYPE_SMALL_IMAGE))


    internal fun getComplicationId(complicationLocation: ComplicationLocation): Int =
        // Add any other supported locations here.
        when (complicationLocation) {
            BACKGROUND -> BACKGROUND_COMPLICATION_ID
            LEFT -> LEFT_COMPLICATION_ID
            RIGHT -> RIGHT_COMPLICATION_ID
            else -> -1
        }

    internal fun getComplicationIds(): IntArray = COMPLICATION_IDS

    internal fun getSupportedComplicationTypes(complicationLocation: ComplicationLocation) =
        // Add any other supported locations here.
        when (complicationLocation) {
            BACKGROUND -> COMPLICATION_SUPPORTED_TYPES[0]
            LEFT -> COMPLICATION_SUPPORTED_TYPES[1]
            RIGHT -> COMPLICATION_SUPPORTED_TYPES[2]
            else -> intArrayOf()
        }

    /**
     * Call this every time you get an onPropertiesChanged() event.
     */
    fun updateProperties(ambientLowBit: Boolean, burnInProtection: Boolean) =
        complicationDrawableMap.values.forEach {
            it.setLowBitAmbient(ambientLowBit)
            it.setBurnInProtection(burnInProtection)
        }

    /**
     * Call this every time the ambient mode changes.
     */
    fun updateAmbientMode(ambientMode: Boolean) =
        complicationDrawableMap.values.forEach { it.setInAmbientMode(ambientMode) }

    /**
     * Call this when the size of the screen changes, which is to say, at least once
     * in the beginning. Note that we're ignoring the presence or absence of a flat tire.
     */
    fun updateBounds(width: Int, height: Int) {
        val sizeOfComplication = width / 4
        val midpointOfScreen = width / 2

        val horizontalOffset = (midpointOfScreen - sizeOfComplication) / 2
        val verticalOffset = midpointOfScreen - sizeOfComplication / 2

        val leftBounds = Rect(
                // Left, Top, Right, Bottom
                horizontalOffset,
                verticalOffset,
                horizontalOffset + sizeOfComplication,
                verticalOffset + sizeOfComplication)

        val rightBounds = Rect(
                // Left, Top, Right, Bottom
                midpointOfScreen + horizontalOffset,
                verticalOffset,
                midpointOfScreen + horizontalOffset + sizeOfComplication,
                verticalOffset + sizeOfComplication)

        val screenForBackgroundBound = Rect(0, 0, width, height)

        complicationDrawableMap[LEFT_COMPLICATION_ID]?.setBounds(leftBounds)
        complicationDrawableMap[RIGHT_COMPLICATION_ID]?.setBounds(rightBounds)
        complicationDrawableMap[BACKGROUND_COMPLICATION_ID]?.setBounds(screenForBackgroundBound)
    }

    /**
     * Call this whenever you get an onComplicationUpdated() event.
     */
    fun updateComplication(complicationId: Int, complicationData: ComplicationData?) {
        verbose { "onComplicationDataUpdate() id: " + complicationId }

        complicationDrawableMap[complicationId]?.setComplicationData(complicationData)
        if (complicationData == null)
            complicationDataMap -= complicationId;
        else
            complicationDataMap += complicationId to complicationData
    }

    private var complicationDrawableMap: Map<Int,ComplicationDrawable> = emptyMap()
    private var complicationDataMap: Map<Int,ComplicationData> = emptyMap()
    private var activeComplicationDrawableMap: Map<Int,ComplicationDrawable> = emptyMap()

    private fun initializeComplicationsAndBackground() {
        verbose("initializeComplications()")

        val context = watchFace // RuntimeException if the watchface was never initialized

        // Creates a ComplicationDrawable for each location where the user can render a
        // complication on the watch face. In this watch face, we create one for left, right,
        // and background, but you could add many more.
        complicationDrawableMap += LEFT_COMPLICATION_ID to ComplicationDrawable(context)
        complicationDrawableMap += RIGHT_COMPLICATION_ID to ComplicationDrawable(context)
        complicationDrawableMap += BACKGROUND_COMPLICATION_ID to ComplicationDrawable(context).apply {
            // custom settings here: black unless an image bitmap makes it otherwise
            setBackgroundColorActive(Color.BLACK)
        }
    }

    /**
     * Call this in your onCreate() to initialize the complications for the engine.
     */
    fun setActiveComplications(engine: CanvasWatchFaceService.Engine) =
        engine.setActiveComplications(*COMPLICATION_IDS)

    /**
     * Some complications types are "tappable" and others aren't.
     */
    private fun isTappableComplicationType(type: Int?) = when (type) {
        null, TYPE_EMPTY, TYPE_LARGE_IMAGE, TYPE_NOT_CONFIGURED -> false
        else -> true
    }

    /**
     * Call this if the watchface complication is of the NO_PERMISSION type, so we need
     * to request the permission.
     */
    private fun launchPermissionForComplication() =
        // kinda baffling that this is even necessary; why aren't permissions dealt with when you add the complication?
        watchFace.startActivity(
                ComplicationHelperActivity.createPermissionRequestHelperIntent(
                        watchFace, ComponentName(watchFace, watchFace::class.java)))

    /**
     * Call this if you've got a tap event that might belong to the complication system. As a side
     * effect, may launch a permission dialog.
     */
    fun handleTap(x: Int, y: Int, currentTime: Long) {
        val tappedComplicationIds = complicationDrawableMap.keys
                // first, we aren't sending clicks to the background, and we're supposed to ignore anything
                // that isn't "active" right now or that isn't "tappable", and of course, whatever it
                // is, the tap has to actually be inside the bounds of the complication. (so complicated!)
                .filter { it != BACKGROUND_COMPLICATION_ID
                        && complicationDataMap[it]?.isActive(currentTime) ?: false
                        && isTappableComplicationType(complicationDataMap[it]?.type)
                        && complicationDrawableMap[it]?.bounds?.contains(x, y) ?: false }

        verbose { "Complication tap, hits on complication(s): " + tappedComplicationIds.joinToString() }

        // we're only going to use the head of the list; it's going to basically never happen
        // that we have more than one hit here, since we're explicitly ignoring the background
        // image complication for tap events.

        if (tappedComplicationIds.size > 1)
            warn { "Whoa, more than one complication hit: ${tappedComplicationIds.size}" }

        if (tappedComplicationIds.isNotEmpty()) {
            val complicationData = complicationDataMap[tappedComplicationIds.first()]
            val tapAction = complicationData?.tapAction
            when {
                tapAction != null -> tapAction.send()
                complicationData?.type == TYPE_NO_PERMISSION -> launchPermissionForComplication()
                else -> warn { "Tapped complication with no actions to take!" }
            }
        }
    }
}

sealed class MenuGroup
data class RadioGroup(val entries: List<Pair<Drawable, Int>>, val default: Int, val callback: (Int)->Unit): MenuGroup()
data class Toggle(val drawable: Drawable, val enabled: Boolean, val callBack: (Boolean)->Unit): MenuGroup()
