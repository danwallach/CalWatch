/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch2

object Constants {
    const val PREFS_KEY = "org.dwallach.calwatch2.prefs"
    const val DATA_KEY = "org.dwallach.calwatch2.data"
    const val SETTINGS_PATH = "/settings"
    const val DEFAULT_WATCHFACE = ClockState.FACE_TOOL
    const val DEFAULT_SHOW_SECONDS = true
    const val DEFAULT_SHOW_DAY_DATE = true
    const val DEFAULT_SHOW_STEP_COUNTER = false
    const val POWER_WARN_LOW_LEVEL = 0.33f
    const val POWER_WARN_CRITICAL_LEVEL = 0.1f
}
