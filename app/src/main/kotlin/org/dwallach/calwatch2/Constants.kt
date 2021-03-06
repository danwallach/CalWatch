/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2019 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch2

import org.dwallach.complications.ComplicationLocation.RIGHT
import org.dwallach.complications.ComplicationLocation.TOP
import org.dwallach.complications.ComplicationLocation.BOTTOM

object Constants {
    const val PREFS_KEY = "org.dwallach.calwatch2.prefs"
    const val DATA_KEY = "org.dwallach.calwatch2.data"
    const val SETTINGS_PATH = "/settings"
    const val DEFAULT_WATCHFACE = ClockState.FACE_TOOL
    const val DEFAULT_SHOW_SECONDS = true
    const val DEFAULT_SHOW_DAY_DATE = true
    const val POWER_WARN_LOW_LEVEL = 0.33f
    const val POWER_WARN_CRITICAL_LEVEL = 0.1f
    val COMPLICATION_LOCATIONS = listOf(RIGHT, TOP, BOTTOM)
}
