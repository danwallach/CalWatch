package org.dwallach.complications

import android.support.wearable.watchface.CanvasWatchFaceService
import org.jetbrains.anko.AnkoLogger

/**
 * APIs that bridge from our normal watchface into our adapted code from the Android sample watchface.
 */

object ComplicationManager: AnkoLogger {
    fun init(): Unit {

    }

    fun getComplicationId(complicationLocation: AnalogComplicationConfigRecyclerViewAdapter.ComplicationLocation): Int {
        throw NotImplementedError("not implemented yet")
    }

    fun getComplicationIds(): IntArray {
        throw NotImplementedError("not implemented yet")
    }

    fun getWatchFaceService(): CanvasWatchFaceService {
        throw NotImplementedError("not implemented yet")
    }

    fun getSupportedComplicationTypes(complicationLocation: AnalogComplicationConfigRecyclerViewAdapter.ComplicationLocation): IntArray {
        throw NotImplementedError("not implemented yet")
    }

}
