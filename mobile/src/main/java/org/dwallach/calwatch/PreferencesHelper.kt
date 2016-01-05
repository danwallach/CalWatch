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
    private val TAG = "PreferencesHelper"

    fun savePreferences(context: Context) {
        Log.v(TAG, "savePreferences")
        val prefs = context.getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val clockState = ClockState.getState()

        editor.putInt("faceMode", clockState.faceMode)
        editor.putBoolean("showSeconds", clockState.showSeconds)
        editor.putBoolean("showDayDate", clockState.showDayDate)

        if (!editor.commit())
            Log.e(TAG, "savePreferences commit failed ?!")
    }

    fun loadPreferences(context: Context) {
        Log.v(TAG, "loadPreferences")

        val clockState = ClockState.getState()

        val prefs = context.getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE)
        val faceMode = prefs.getInt("faceMode", Constants.DefaultWatchFace) // ClockState.FACE_TOOL
        val showSeconds = prefs.getBoolean("showSeconds", Constants.DefaultShowSeconds)
        val showDayDate = prefs.getBoolean("showDayDate", Constants.DefaultShowDayDate)

        Log.v(TAG, "faceMode: $faceMode, showSeconds: $showSeconds, showDayDate: $showDayDate")

        clockState.faceMode = faceMode
        clockState.showSeconds = showSeconds
        clockState.showDayDate = showDayDate

        clockState.pingObservers() // we only need to do this once, versus multiple times when done internally
    }
}
