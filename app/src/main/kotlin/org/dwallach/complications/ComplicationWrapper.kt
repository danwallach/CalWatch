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
import org.dwallach.calwatch.errorLogAndThrow
import org.jetbrains.anko.warn


/**
 * The goal is this is to provide a super minimal interface for two things: feeding in configuration
 * data for menus (the complication selectors will be added in automatically) via [loadMenus],
 * drawing the complications via [drawComplications], etc.
 *
 * Code here began its life in the example AnalogWatchFaceService but has been largely rewritten
 * to be relatively independent of the watchface itself, to remove color-setting options, and
 * to use Kotlin's nice features when appropriate.
 */
object ComplicationWrapper : AnkoLogger {
    private var watchFaceRef: WeakReference<CanvasWatchFaceService?>? = null
    private var watchFaceClassInternal: Class<out CanvasWatchFaceService>? = null

    /**
     * Here's a property to get the CanvasWatchFaceService in use. Set it at startup time.
     * Storage internally is a weak reference, so it's possible, albeit unlikely, that this
     * will fail, causing a RuntimeException. Yeah, that kinda sucks, but if the watchface
     * is active, this won't be an issue. We're using a WeakReference to make sure that
     * we're not accidentally keeping anything live that really should die at cleanup time.
     */
    private var watchFace: CanvasWatchFaceService
        get() = watchFaceRef?.get() ?: errorLogAndThrow("no watchface service found!")
        set(watchFace) {
            verbose { "Saving watchface service ref in complication wrapper" }
            watchFaceRef = WeakReference(watchFace)
            watchFaceClassInternal = watchFace::class.java
        }

    /**
     * If all we need is a reference to the *class* of the watchface service, then this
     * property will have it, even if the watchface itself is garbage collected.
     */
    val watchFaceClass: Class<out CanvasWatchFaceService>
        get() = watchFaceClassInternal ?: errorLogAndThrow("no watchface class ref found!")

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
    internal const val BACKGROUND_COMPLICATION_ID = 0

    internal const val LEFT_COMPLICATION_ID = 100
    internal const val RIGHT_COMPLICATION_ID = 101
    internal const val TOP_COMPLICATION_ID = 102
    internal const val BOTTOM_COMPLICATION_ID = 103

    internal lateinit var activeLocations: Array<out ComplicationLocation>
    internal lateinit var activeComplicationIds: IntArray

    internal val complicationIds get() = activeComplicationIds // read-only access

    // Left and right dial supported types.
    private lateinit var activeComplicationSupportedTypes: Array<IntArray>


    internal fun getComplicationId(complicationLocation: ComplicationLocation): Int =
        when (complicationLocation) {
            BACKGROUND -> BACKGROUND_COMPLICATION_ID
            LEFT -> LEFT_COMPLICATION_ID
            RIGHT -> RIGHT_COMPLICATION_ID
            TOP -> TOP_COMPLICATION_ID
            BOTTOM -> BOTTOM_COMPLICATION_ID
        }

    internal fun getSupportedComplicationTypes(complicationLocation: ComplicationLocation) =
        when (complicationLocation) {
            BACKGROUND -> intArrayOf(TYPE_LARGE_IMAGE)
            LEFT, RIGHT, TOP, BOTTOM -> intArrayOf(TYPE_RANGED_VALUE, TYPE_ICON, TYPE_SHORT_TEXT, TYPE_SMALL_IMAGE)
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

        complicationDrawableMap[LEFT_COMPLICATION_ID]?.bounds = Rect(
                // Left, Top, Right, Bottom
                horizontalOffset,
                verticalOffset,
                horizontalOffset + sizeOfComplication,
                verticalOffset + sizeOfComplication)

        complicationDrawableMap[RIGHT_COMPLICATION_ID]?.bounds = Rect(
                // Left, Top, Right, Bottom
                midpointOfScreen + horizontalOffset,
                verticalOffset,
                midpointOfScreen + horizontalOffset + sizeOfComplication,
                verticalOffset + sizeOfComplication)

        // note that we've just swapped the x and y values for TOP to be the same as LEFT
        complicationDrawableMap[TOP_COMPLICATION_ID]?.bounds = Rect(
                // Left, Top, Right, Bottom
                verticalOffset,
                horizontalOffset,
                verticalOffset + sizeOfComplication,
                horizontalOffset + sizeOfComplication)

        // note that we've just swapped the x and y values for BOTTOM to be the same as RIGHT
        complicationDrawableMap[BOTTOM_COMPLICATION_ID]?.bounds = Rect(
                // Left, Top, Right, Bottom
                verticalOffset,
                midpointOfScreen + horizontalOffset,
                verticalOffset + sizeOfComplication,
                midpointOfScreen + horizontalOffset + sizeOfComplication)

        complicationDrawableMap[BACKGROUND_COMPLICATION_ID]?.bounds = Rect(0, 0, width, height)

        // at this point, we know the width and height, which also means that the PaintCan styles
        // have been initialized (since they're a function of screen size as well), so it's safe
        // to apply some styles to our complications.

        complicationDrawableMap.values.forEach(stylingFunc)
    }

    /**
     * Call this whenever you get an onComplicationUpdated() event.
     */
    fun updateComplication(complicationId: Int, complicationData: ComplicationData?) {
        verbose { "onComplicationDataUpdate() id: $complicationId" }

        complicationDrawableMap[complicationId]?.setComplicationData(complicationData)
        if (complicationData == null)
            // when we get back no complication data, that's the only signal we get
            // that a complication has been killed, so we're just going to remove the
            // entry from our map; see also isVisible()
            complicationDataMap -= complicationId
        else
            complicationDataMap += complicationId to complicationData
    }

    /**
     * Call this if you want to know if a given complication is visible to the user at the moment.
     * On a watchface, you might use this to decide whether to show or hide numerals.
     */
    fun isVisible(location: ComplicationLocation) =
            complicationDataMap.containsKey(getComplicationId(location))


    /**
     * Call this if you want to know whether the watchface has enabled a given complication location.
     * This pretty much just queries the locations specified in [init].
     */
    fun isEnabled(location: ComplicationLocation) =
        complicationDrawableMap.containsKey(getComplicationId(location))

    private var complicationDrawableMap: Map<Int,ComplicationDrawable> = emptyMap()
    private var complicationDataMap: Map<Int,ComplicationData> = emptyMap()

    /**
     * Call this in your onCreate() to initialize the complications for the engine. If no locations
     * are specified, then you get the default (BACKGROUND, LEFT, RIGHT, TOP, and BOTTOM).
     */
    fun init(watchFace: CanvasWatchFaceService, engine: CanvasWatchFaceService.Engine, vararg locations: ComplicationLocation) {
        verbose { "Complication locations: $locations" }

        this.watchFace = watchFace  // creates a weakref, initializes many things

        activeLocations = locations
        activeComplicationIds = locations.map(this::getComplicationId).toIntArray()
        activeComplicationSupportedTypes = locations.map(this::getSupportedComplicationTypes).toTypedArray()

        // Creates a ComplicationDrawable for each location where the user can render a
        // complication on the watch face. Bonus coolness for Kotlin's associate method letting
        // us convert from an array to a map in one go. Functional programming FTW!
        complicationDrawableMap = activeComplicationIds.associate { it to ComplicationDrawable(watchFace) }

        // custom settings here: black unless an image bitmap makes it otherwise
        complicationDrawableMap[BACKGROUND_COMPLICATION_ID]?.setBackgroundColorActive(Color.BLACK)

        engine.setActiveComplications(*activeComplicationIds)
    }

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
                        watchFace, ComponentName(watchFace, watchFaceClass)))

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

    // initially a no-op, will replace later when styleComplications is called
    private var stylingFunc: (ComplicationDrawable) -> Unit = {
        warn { "Default styles being applied to complications" }
    }

    /**
     * Call this to set a lambda which will be called with each ComplicationDrawable to style it.
     * Note that this will only style your foreground complications. The background is only for
     * full-screen images, so no styling happens there.
     */
    fun styleComplications(func: (ComplicationDrawable)->Unit) {
        stylingFunc = func
    }
}

sealed class MenuGroup
data class RadioGroup(val entries: List<Pair<Drawable, Int>>, val default: Int, val callback: (Int)->Unit): MenuGroup()
data class Toggle(val drawable: Drawable, val enabled: Boolean, val callBack: (Boolean)->Unit): MenuGroup()
