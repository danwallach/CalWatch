/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
@file:JvmName("org.dwallach.calwatch.PhoneActivity")
package org.dwallach.calwatch

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View

import java.lang.ref.WeakReference
import java.util.Observable
import java.util.Observer

import kotlinx.android.synthetic.main.activity_phone.*

class PhoneActivity : Activity(), Observer {
    private var disableUICallbacks = false

    init {
        activityRef = WeakReference<Activity>(this)
    }

    //
    // this will be called, eventually, from whatever feature is responsible for
    // restoring saved user preferences
    //
    private fun setFaceModeUI(mode: Int, showSecondsP: Boolean, showDayDateP: Boolean) {
        Log.v(TAG, "setFaceModeUI")
        if (!uiButtonsReady()) {
            Log.v(TAG, "trying to set UI mode without buttons active yet")
            return
        }

        disableUICallbacks = true

        try {
            when (mode) {
                ClockState.FACE_TOOL -> toolButton.performClick()
                ClockState.FACE_NUMBERS -> numbersButton.performClick()
                ClockState.FACE_LITE -> liteButton.performClick()
                else -> Log.v(TAG, "bogus face mode: " + mode)
            }

            this.showSeconds.isChecked = showSecondsP
            this.showDayDate.isChecked = showDayDateP
        } catch (throwable: Throwable) {
            // probably a called-from-wrong-thread-exception, we'll just ignore it
            Log.v(TAG, "ignoring exception while updating button state")
        }

        disableUICallbacks = false
    }

    private fun uiButtonsReady() :Boolean =
            toolButton != null && numbersButton != null && liteButton != null && showSeconds != null && showDayDate != null;

    private fun getFaceModeFromUI() {
        Log.v(TAG, "getFaceModeFromUI")
        var mode = -1

        if (!uiButtonsReady()) {
            Log.v(TAG, "trying to get UI mode without buttons active yet")
            return
        }

        if (toolButton.isChecked)
            mode = ClockState.FACE_TOOL
        else if (numbersButton.isChecked)
            mode = ClockState.FACE_NUMBERS
        else if (liteButton.isChecked)
            mode = ClockState.FACE_LITE
        else
            Log.v(TAG, "no buttons are selected? weird.")

        val showSeconds = showSeconds.isChecked
        val showDayDate = showDayDate.isChecked
        val clockState = ClockState.getState()

        if (mode != -1) {
            clockState.faceMode = mode
        }
        clockState.showSeconds = showSeconds
        clockState.showDayDate = showDayDate

        clockState.pingObservers() // we only need to do this once, versus a whole bunch of times when it was happening internally
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.v(TAG, "Create!")

        setContentView(R.layout.activity_phone)
    }

    override fun onPause() {
        super.onPause()
        Log.v(TAG, "Pause!")

        // perhaps incorrect assumption: if our activity is being killed, onStop will happen beforehand,
        // so we'll deregister our clockState observer, allowing this Activity object to become
        // garbage. A new one will be created if the activity ever comes back to life, which
        // will call getClockState(), which will in turn resurrect observer. Setting clockState=null
        // means that, even if this specific Activity object is resurrected from the dead, we'll
        // just reconnect it the next time somebody internally calls getClockState(). No harm, no foul.

        // http://developer.android.com/reference/android/app/Activity.html

        if (surfaceView != null)
            surfaceView.kill(this)

        ClockState.getState().deleteObserver(this)
    }

    override fun onStart() {
        super.onStart()
        Log.v(TAG, "Start!")

        val myListener = View.OnClickListener {
            if (!disableUICallbacks)
                getFaceModeFromUI()
            surfaceView.invalidate();
        }

        liteButton.setOnClickListener(myListener)
        toolButton.setOnClickListener(myListener)
        numbersButton.setOnClickListener(myListener)
        showSeconds.setOnClickListener(myListener)
        showDayDate.setOnClickListener(myListener)

        WatchCalendarService.kickStart(this)  // bring it up, if it's not already up
        PreferencesHelper.loadPreferences(this)
        CalendarPermission.init(this)

        surfaceView.init(this)
        surfaceView.initCalendarFetcher(this)

        onResume()
        Log.v(TAG, "activity setup complete")
    }

    override fun onResume() {
        super.onResume()
        Log.v(TAG, "Resume!")

        ClockState.getState().addObserver(this)

        if (surfaceView != null) {
            surfaceView.init(this)
            surfaceView.invalidate()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        //        getMenuInflater().inflate(R.menu.phone, menu);
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
        Log.v(TAG, "Noticed a change in the clock state; saving preferences")

        val clockState = ClockState.getState()
        setFaceModeUI(clockState.faceMode, clockState.showSeconds, clockState.showDayDate)
        PreferencesHelper.savePreferences(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        Log.v(TAG, "onRequestPermissionsResult")
        CalendarPermission.handleResult(requestCode, permissions, grantResults)

        if (surfaceView != null) {
            Log.e(TAG, "no clockview available?!")
        } else {
            surfaceView.initCalendarFetcher(this)
        }

        Log.v(TAG, "finishing PermissionActivity")
    }

    companion object {
        private val TAG = "PhoneActivity"

        private var activityRef: WeakReference<Activity>? = null

        /**
         * This will be called when the user clicks on the watchface, presumably because they want
         * us to request calendar permissions.
         */
        internal fun watchfaceClick(view: MyViewAnim) {
            Log.v(TAG, "Watchface clicked!")
            if (activityRef == null)
                return  // can't do anything without an activity

            val activity = activityRef?.get() ?: return
            // can't do anything with an activity

            if (!ClockState.getState().calendarPermission) {
                Log.v(TAG, "Requesting permissions")
                CalendarPermission.request(activity)
                view.initCalendarFetcher(activity)
            } else {
                Log.v(TAG, "Permissions already granted.")
            }
        }
    }
}
