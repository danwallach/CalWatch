/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.app.Activity
import android.content.ContextWrapper
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View

import java.util.Observable
import java.util.Observer

import kotlinx.android.synthetic.main.activity_phone_intl.*
import org.jetbrains.anko.*

class PhoneActivity : Activity(), Observer, AnkoLogger {
    private var disableUICallbacks = false

    //
    // this will be called, eventually, from whatever feature is responsible for
    // restoring saved user preferences
    //
    private fun setFaceModeUI(mode: Int, showSecondsP: Boolean, showDayDateP: Boolean, showStepCounterP: Boolean) {
        verbose("setFaceModeUI")
        if (!uiButtonsReady()) {
            verbose("trying to set UI mode without buttons active yet")
            return
        }

        disableUICallbacks = true

        try {
            when (mode) {
                ClockState.FACE_TOOL -> toolButton.performClick()
                ClockState.FACE_NUMBERS -> numbersButton.performClick()
                ClockState.FACE_LITE -> liteButton.performClick()
                else -> verbose("bogus face mode: $mode")
            }

            showSeconds.isChecked = showSecondsP
            showDayDate.isChecked = showDayDateP
            showStepCounter.isChecked = showStepCounterP

            // while we're here, we'll also put the proper day/date into the relevant button
            val dayOfWeek = TimeWrapper.localDayOfWeek()
            val monthDay = TimeWrapper.localMonthDay()
            dayDateButton.text = "$monthDay\n$dayOfWeek"

        } catch (throwable: Throwable) {
            // probably a called-from-wrong-thread-exception, we'll just ignore it
            verbose("ignoring exception while updating button state")
        }

        disableUICallbacks = false
    }

    private fun uiButtonsReady() :Boolean =
            toolButton != null && numbersButton != null && liteButton != null && showSeconds != null && showDayDate != null

    private fun getFaceModeFromUI() {
        verbose("getFaceModeFromUI")

        if (!uiButtonsReady()) {
            verbose("trying to get UI mode without buttons active yet")
            return
        }

        ClockState.faceMode = when {
            toolButton.isChecked -> ClockState.FACE_TOOL
            numbersButton.isChecked -> ClockState.FACE_NUMBERS
            liteButton.isChecked -> ClockState.FACE_LITE
            else -> {
                error("no buttons are selected? weird.")
                ClockState.faceMode // we'll go with whatever's already there, nothing better to choose
            }
        }

        ClockState.showSeconds = showSeconds.isChecked
        ClockState.showDayDate = showDayDate.isChecked
        ClockState.showStepCounter = showStepCounter.isChecked

        verbose { "new state -- showSeconds: ${ClockState.showSeconds}, showDayDate: ${ClockState.showDayDate}, showStepCounter: ${ClockState.showStepCounter}" }

        ClockState.pingObservers() // we only need to do this once
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        verbose("Create!")

        setContentView(R.layout.activity_phone_intl)
    }

    override fun onPause() {
        super.onPause()
        verbose("Pause!")

        surfaceView.pause(this)

        ClockState.deleteObserver(this)
    }

    override fun onStart() {
        super.onStart()
        verbose("Start!")

        val myListener = View.OnClickListener {
            if (!disableUICallbacks)
                getFaceModeFromUI()
            surfaceView.invalidate()
        }

        liteButton.setOnClickListener(myListener)
        toolButton.setOnClickListener(myListener)
        numbersButton.setOnClickListener(myListener)
        showSeconds.setOnClickListener(myListener)
        showDayDate.setOnClickListener(myListener)
        showStepCounter.setOnClickListener(myListener)

        // Each of these handles the big image buttons that we want to also cause
        // their switches to toggle.

        secondsImageButton.setOnClickListener {
            showSeconds.toggle()
            getFaceModeFromUI()
            surfaceView.invalidate()
        }

        dayDateButton.setOnClickListener {
            showDayDate.toggle()
            getFaceModeFromUI()
            surfaceView.invalidate()
        }

        stepCountImageButton.setOnClickListener {
            showStepCounter.toggle()
            getFaceModeFromUI()
            surfaceView.invalidate()
        }

        WatchCalendarService.kickStart(this)  // bring it up, if it's not already up
        PreferencesHelper.loadPreferences(this)
        CalendarPermission.init(this)
        GoogleApiWrapper.startConnection(this) { verbose { "GoogleApi ready" } }

        surfaceView.init(this)
        surfaceView.initCalendarFetcher(this)

        onResume()
        verbose("activity setup complete")
    }

    override fun onResume() {
        super.onResume()
        verbose("Resume!")

        GoogleApiWrapper.startConnection(this) { verbose { "GoogleApi ready" } }
        ClockState.addObserver(this)
        surfaceView.resume(this)
        surfaceView.invalidate()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        //        getMenuInflater().inflate(R.menu.phone, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return super.onOptionsItemSelected(item)
    }

    override fun update(observable: Observable?, data: Any?) {
        // somebody changed *something* in the ClockState, causing us to get called
        verbose("Noticed a change in the clock state; saving preferences")

        setFaceModeUI(ClockState.faceMode, ClockState.showSeconds, ClockState.showDayDate, ClockState.showStepCounter)
        PreferencesHelper.savePreferences(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        verbose("onRequestPermissionsResult")

        CalendarPermission.handleResult(requestCode, permissions, grantResults)
        surfaceView.initCalendarFetcher(this)

        verbose("finishing PermissionActivity")
    }

    companion object: AnkoLogger {
        /**
         * This will be called when the user clicks on the watchface, presumably because they want
         * us to request calendar permissions.
         */
        fun watchfaceClick(view: MyViewAnim) {
            verbose("Watchface clicked!")

            // can't do anything without an activity
            val activity = view.toActivity() ?: return

            if (!ClockState.calendarPermission) {
                verbose("Requesting permissions")
                CalendarPermission.request(activity)
                view.initCalendarFetcher(activity)
            } else {
                verbose("Permissions already granted.")
            }
        }
    }
}

/**
 * Helper extension function to convert from a view to its surrounding activity, if that activity exists.
 */
fun View.toActivity(): Activity? {
    // See: http://stackoverflow.com/questions/8276634/android-get-hosting-activity-from-a-view
    var context = this.context
    while(context is ContextWrapper) {
        if(context is Activity) {
            return context
        } else {
            context = context.baseContext
        }
    }
    return null
}
