/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object PreferencesHelper {
    private const val TAG = "PreferencesHelper"

    fun savePreferences(context: Context) {
        Log.v(TAG, "savePreferences")
        val prefs = context.getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putInt("faceMode", ClockState.faceMode)
        editor.putBoolean("showSeconds", ClockState.showSeconds)
        editor.putBoolean("showDayDate", ClockState.showDayDate)

        if (!editor.commit())
            Log.e(TAG, "savePreferences commit failed ?!")
    }

    fun loadPreferences(context: Context) {
        Log.v(TAG, "loadPreferences")

        val prefs = context.getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE)
        val faceMode = prefs.getInt("faceMode", Constants.DefaultWatchFace) // ClockState.FACE_TOOL
        val showSeconds = prefs.getBoolean("showSeconds", Constants.DefaultShowSeconds)
        val showDayDate = prefs.getBoolean("showDayDate", Constants.DefaultShowDayDate)

        Log.v(TAG, "faceMode: $faceMode, showSeconds: $showSeconds, showDayDate: $showDayDate")

        ClockState.faceMode = faceMode
        ClockState.showSeconds = showSeconds
        ClockState.showDayDate = showDayDate

        ClockState.pingObservers() // we only need to do this once, versus multiple times when done internally
    }
}
