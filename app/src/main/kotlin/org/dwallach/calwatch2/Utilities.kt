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
