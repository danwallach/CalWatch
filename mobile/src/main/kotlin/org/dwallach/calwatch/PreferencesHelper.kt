/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.content.Context
import org.jetbrains.anko.*

object PreferencesHelper: AnkoLogger {
    fun savePreferences(context: Context) {
        verbose("savePreferences")

        context.getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE).edit().apply {
            putInt("faceMode", ClockState.faceMode)
            putBoolean("showSeconds", ClockState.showSeconds)
            putBoolean("showDayDate", ClockState.showDayDate)

            if (!commit())
                error("savePreferences commit failed ?!")
        }
    }

    fun loadPreferences(context: Context) {
        verbose("loadPreferences")

        context.getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE).apply {
            val faceMode = getInt("faceMode", Constants.DefaultWatchFace) // ClockState.FACE_TOOL
            val showSeconds = getBoolean("showSeconds", Constants.DefaultShowSeconds)
            val showDayDate = getBoolean("showDayDate", Constants.DefaultShowDayDate)

            verbose { "faceMode: $faceMode, showSeconds: $showSeconds, showDayDate: $showDayDate" }

            ClockState.faceMode = faceMode
            ClockState.showSeconds = showSeconds
            ClockState.showDayDate = showDayDate
        }

        ClockState.pingObservers() // we only need to do this once, versus multiple times when done internally
    }
}
