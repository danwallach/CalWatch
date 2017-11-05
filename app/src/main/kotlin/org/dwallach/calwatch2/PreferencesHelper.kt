/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch2

import android.annotation.SuppressLint
import android.content.Context
import org.jetbrains.anko.*

object PreferencesHelper: AnkoLogger {
    @SuppressLint("CommitPrefEdits")
    fun savePreferences(context: Context) {
        with (context.getSharedPreferences(Constants.PREFS_KEY, Context.MODE_PRIVATE).edit()) {
            putInt("faceMode", ClockState.faceMode)
            putBoolean("showSeconds", ClockState.showSeconds)
            putBoolean("showDayDate", ClockState.showDayDate)
            putBoolean("showStepCounter", ClockState.showStepCounter)
            putInt("preferencesVersion", 3)

            verbose { "savePreferences: ${ClockState.faceMode}, showSeconds: ${ClockState.showSeconds}, showDayDate: ${ClockState.showDayDate}, showStepCounter: ${ClockState.showStepCounter}" }

            if (!commit())
                error("savePreferences commit failed ?!")
        }
    }

    /**
     * Updates the preferences in ClockState, returns an integer version number. So far,
     * the choices are "0", meaning it didn't find a version number in placed, and "3",
     * which is the newest version. This will help with legacy migration. (If the version
     * is zero, then some of the values in ClockState will have been set from the defaults.)
     */
    fun loadPreferences(context: Context) : Int {
        with (context.getSharedPreferences(Constants.PREFS_KEY, Context.MODE_PRIVATE)) {
            val faceMode = getInt("faceMode", Constants.DEFAULT_WATCHFACE)
            val showSeconds = getBoolean("showSeconds", Constants.DEFAULT_SHOW_SECONDS)
            val showDayDate = getBoolean("showDayDate", Constants.DEFAULT_SHOW_DAY_DATE)
            val showStepCounter = getBoolean("showStepCounter", Constants.DEFAULT_SHOW_STEP_COUNTER)
            val version = getInt("preferencesVersion", 0)

            verbose { "loadPreferences: $faceMode, showSeconds: $showSeconds, showDayDate: $showDayDate, showStepCounter: $showStepCounter, preferencesVersion: $version" }

            ClockState.faceMode = faceMode
            ClockState.showSeconds = showSeconds
            ClockState.showDayDate = showDayDate
            ClockState.showStepCounter = showStepCounter

            ClockState.pingObservers() // we only need to do this once, versus multiple times when done internally

            return version
        }

        // Kotlin engineering note: return inside of a lambda will return from the nearest enclosing `fun`,
        // so the above code has the desired effect.
        // https://www.reddit.com/r/Kotlin/comments/3yybyf/returning_from_lambda_functions/?

        // Curiously, this only really works because `with` is an inline function
        // https://kotlinlang.org/docs/reference/inline-functions.html#non-local-returns
    }
}
