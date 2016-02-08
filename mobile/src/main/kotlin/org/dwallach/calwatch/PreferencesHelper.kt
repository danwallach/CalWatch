/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.content.Context
import android.util.Log

object PreferencesHelper {
    private const val TAG = "PreferencesHelper"

    fun savePreferences(context: Context) {
        Log.v(TAG, "savePreferences")

        context.getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE).edit().apply {
            putInt("faceMode", ClockState.faceMode)
            putBoolean("showSeconds", ClockState.showSeconds)
            putBoolean("showDayDate", ClockState.showDayDate)

            if (!commit())
                Log.e(TAG, "savePreferences commit failed ?!")
        }
    }

    fun loadPreferences(context: Context) {
        Log.v(TAG, "loadPreferences")

        context.getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE).apply {
            val faceMode = getInt("faceMode", Constants.DefaultWatchFace) // ClockState.FACE_TOOL
            val showSeconds = getBoolean("showSeconds", Constants.DefaultShowSeconds)
            val showDayDate = getBoolean("showDayDate", Constants.DefaultShowDayDate)

            Log.v(TAG, "faceMode: $faceMode, showSeconds: $showSeconds, showDayDate: $showDayDate")

            ClockState.faceMode = faceMode
            ClockState.showSeconds = showSeconds
            ClockState.showDayDate = showDayDate
        }

        ClockState.pingObservers() // we only need to do this once, versus multiple times when done internally
    }
}
