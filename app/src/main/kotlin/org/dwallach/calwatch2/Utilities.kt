/*
 * CalWatch
 * Copyright (C) 2014-2018 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import org.dwallach.complications.AnalogComplicationConfigRecyclerViewAdapter
import org.jetbrains.anko.AnkoLogger

object Utilities: AnkoLogger {
    fun redrawEverything() {
        ClockFace.wipeAllCaches()
        AnalogComplicationConfigRecyclerViewAdapter.reloadAllToggles()
        ClockFaceConfigView.redraw()
        CalWatchFaceService.redraw()
    }
}

/**
 * Given any function from K to V, returns another function, also from K to V, which
 * memoizes the results, only calling the internal function exactly once for each input.
 */
fun <K, V: Any, F : (K) -> V> F.memoize(): (K) -> V {
    val map = mutableMapOf<K, V>()
    return {
        if (it in map)
            map[it] ?: kotlin.error("unexpected null from memoized function")
        else {
            val newV = this(it)
            map[it] = newV
            newV
        }
    }
}