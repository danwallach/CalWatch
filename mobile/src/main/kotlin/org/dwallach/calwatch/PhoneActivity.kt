/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RadioButton
import android.widget.Switch

import java.lang.ref.WeakReference
import java.util.Observable
import java.util.Observer


class PhoneActivity : Activity(), Observer {

    private var toolButton: RadioButton? = null
    private var numbersButton: RadioButton? = null
    private var liteButton: RadioButton? = null
    private var clockView: MyViewAnim? = null
    private var secondsSwitch: Switch? = null
    private var dayDateSwitch: Switch? = null

    private var clockState: ClockState? = null
    private var disableUICallbacks = false

    private fun getClockState(): ClockState {
        if (clockState == null) {
            Log.v(TAG, "reconnecting clock state")
            clockState = ClockState.getState()
            clockState!!.addObserver(this)
        }
        return clockState!!
    }

    init {
        activityRef = WeakReference<Activity>(this)
    }

    //
    // this will be called, eventually, from whatever feature is responsible for
    // restoring saved user preferences
    //
    private fun setFaceModeUI(mode: Int, showSeconds: Boolean, showDayDate: Boolean) {
        Log.v(TAG, "setFaceModeUI")
        if (toolButton == null || numbersButton == null || liteButton == null || secondsSwitch == null || dayDateSwitch == null) {
            Log.v(TAG, "trying to set UI mode without buttons active yet")
            return
        }

        disableUICallbacks = true

        try {
            when (mode) {
                ClockState.FACE_TOOL -> toolButton!!.performClick()
                ClockState.FACE_NUMBERS -> numbersButton!!.performClick()
                ClockState.FACE_LITE -> liteButton!!.performClick()
                else -> Log.v(TAG, "bogus face mode: " + mode)
            }

            secondsSwitch!!.isChecked = showSeconds
            dayDateSwitch!!.isChecked = showDayDate
        } catch (throwable: Throwable) {
            // probably a called-from-wrong-thread-exception, we'll just ignore it
            Log.v(TAG, "ignoring exception while updating button state")
        }

        disableUICallbacks = false
    }

    private fun getFaceModeFromUI() {
        Log.v(TAG, "getFaceModeFromUI")
        var mode = -1

        if (toolButton == null || numbersButton == null || liteButton == null || secondsSwitch == null || dayDateSwitch == null) {
            Log.v(TAG, "trying to get UI mode without buttons active yet")
            return
        }

        if (toolButton!!.isChecked)
            mode = ClockState.FACE_TOOL
        else if (numbersButton!!.isChecked)
            mode = ClockState.FACE_NUMBERS
        else if (liteButton!!.isChecked)
            mode = ClockState.FACE_LITE
        else
            Log.v(TAG, "no buttons are selected? weird.")

        val showSeconds = secondsSwitch!!.isChecked
        val showDayDate = dayDateSwitch!!.isChecked

        if (mode != -1) {
            getClockState().faceMode = mode
        }
        getClockState().showSeconds = showSeconds
        getClockState().showDayDate = showDayDate

        getClockState().pingObservers() // we only need to do this once, versus a whole bunch of times when it was happening internally
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

        if (clockView != null)
            clockView!!.kill(this)

        if (clockState != null) {
            clockState!!.deleteObserver(this)
            clockState = null
        }
    }

    override fun onStart() {
        super.onStart()
        Log.v(TAG, "Start!")

        // Core UI widgets: find 'em
        liteButton = findViewById(R.id.liteButton) as RadioButton
        toolButton = findViewById(R.id.toolButton) as RadioButton
        numbersButton = findViewById(R.id.numbersButton) as RadioButton
        clockView = findViewById(R.id.surfaceView) as MyViewAnim
        secondsSwitch = findViewById(R.id.showSeconds) as Switch
        dayDateSwitch = findViewById(R.id.showDayDate) as Switch
        //        clockView.setSleepInEventLoop(true);

        Log.v(TAG, "registering callback")

        val myListener = View.OnClickListener {
            if (!disableUICallbacks)
                getFaceModeFromUI()
            if (clockView != null)
                clockView!!.invalidate()
        }

        liteButton!!.setOnClickListener(myListener)
        toolButton!!.setOnClickListener(myListener)
        numbersButton!!.setOnClickListener(myListener)
        secondsSwitch!!.setOnClickListener(myListener)
        dayDateSwitch!!.setOnClickListener(myListener)

        WatchCalendarService.kickStart(this)  // bring it up, if it's not already up
        PreferencesHelper.loadPreferences(this)
        CalendarPermission.init(this)

        clockView!!.init(this)
        clockView!!.initCalendarFetcher(this)

        onResume()
        Log.v(TAG, "activity setup complete")
    }

    override fun onResume() {
        super.onResume()
        Log.v(TAG, "Resume!")

        getClockState() // side-effects: re-initializes observer

        if (clockView != null) {
            clockView!!.init(this)
            clockView!!.invalidate()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        //        getMenuInflater().inflate(R.menu.phone, menu);
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return super.onOptionsItemSelected(item)
    }

    override fun update(observable: Observable, data: Any) {
        // somebody changed *something* in the ClockState, causing us to get called
        Log.v(TAG, "Noticed a change in the clock state; saving preferences")
        setFaceModeUI(getClockState().faceMode, getClockState().showSeconds, getClockState().showDayDate)
        PreferencesHelper.savePreferences(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        Log.v(TAG, "onRequestPermissionsResult")
        CalendarPermission.handleResult(requestCode, permissions, grantResults)

        if (clockView == null) {
            Log.e(TAG, "no clockview available?!")
        } else {
            clockView!!.initCalendarFetcher(this)
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

            val clockState = ClockState.getState()
            if (!clockState.calendarPermission) {
                Log.v(TAG, "Requesting permissions")
                CalendarPermission.request(activity)
                view.initCalendarFetcher(activity)
            } else {
                Log.v(TAG, "Permissions already granted.")
            }
        }
    }
}
