package org.dwallach.complications

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.support.wearable.watchface.CanvasWatchFaceService
import org.jetbrains.anko.AnkoLogger
import java.lang.ref.WeakReference

/**
 * APIs that bridge from our normal watchface into our adapted code from the Android sample watchface.
 * The goal is this is to provide a super minimal interface for two things: feeding in configuration
 * data for menus (the complication selectors will be added in automatically) via [loadMenus],
 * drawing the complications via [drawComplications], etc.
 */

object ComplicationWrapper : AnkoLogger {
    private var watchFaceRef: WeakReference<CanvasWatchFaceService>? = null

    /**
     * Here's a property to get the CanvasWatchFaceService in use. Set it at startup time.
     * Storage internally is a weak reference, so it's possible, albeit unlikely, that this
     * will fail, causing a RuntimeException. Yeah, that kinda sucks, but if the watchface
     * is active, this won't be an issue. We're using a WeakReference to make sure that
     * we're not accidentally keeping anything live that really should die at cleanup time.
     */
    var watchFace: CanvasWatchFaceService
        get() = watchFaceRef?.get() ?: throw RuntimeException("no watchface service found!")
        set(watchFace: CanvasWatchFaceService) { watchFaceRef = WeakReference(watchFace) }

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
    fun drawComplications(canvas: Canvas) {
        throw NotImplementedError("not implemented yet")
    }

    // TODO bridges for ambient mode, screen resolution, etc.

    internal fun getComplicationId(complicationLocation: AnalogComplicationConfigRecyclerViewAdapter.ComplicationLocation): Int {
        throw NotImplementedError("not implemented yet")
    }

    internal fun getComplicationIds(): IntArray {
        throw NotImplementedError("not implemented yet")
    }

    internal fun getSupportedComplicationTypes(complicationLocation: AnalogComplicationConfigRecyclerViewAdapter.ComplicationLocation): IntArray {
        throw NotImplementedError("not implemented yet")
    }
}

sealed class MenuGroup
data class RadioGroup(val entries: List<Pair<Drawable, Int>>, val default: Int, val callback: (Int)->Unit): MenuGroup()
data class Toggle(val drawable: Drawable, val enabled: Boolean, val callBack: (Boolean)->Unit): MenuGroup()
