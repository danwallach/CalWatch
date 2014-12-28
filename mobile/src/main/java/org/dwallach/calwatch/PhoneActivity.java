/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioButton;
import android.widget.Switch;

import java.util.Observable;
import java.util.Observer;


public class PhoneActivity extends Activity implements Observer {
    private final static String TAG = "PhoneActivity";

    private RadioButton toolButton, numbersButton, liteButton;
    private MyViewAnim clockView;
    private Switch secondsSwitch, dayDateSwitch;

    private ClockState clockState;
    private boolean disableUICallbacks = false;

    private ClockState getClockState() {
        if(clockState == null) {
            Log.v(TAG, "reconnecting clock state");
            clockState = ClockState.getSingleton();
            clockState.addObserver(this);
        }
        return clockState;
    }

    public PhoneActivity() {
        super();
    }

    //
    // this will be called, eventually, from whatever feature is responsible for
    // restoring saved user preferences
    //
    private void setFaceModeUI(int mode, boolean showSeconds, boolean showDayDate) {
        Log.v(TAG, "setFaceModeUI");
        if(toolButton == null || numbersButton == null || liteButton == null || secondsSwitch == null || dayDateSwitch == null) {
            Log.v(TAG, "trying to set UI mode without buttons active yet");
            return;
        }

        disableUICallbacks = true;

        try {
            switch (mode) {
                case ClockState.FACE_TOOL:
                    toolButton.performClick();
                    break;
                case ClockState.FACE_NUMBERS:
                    numbersButton.performClick();
                    break;
                case ClockState.FACE_LITE:
                    liteButton.performClick();
                    break;
                default:
                    Log.v(TAG, "bogus face mode: " + mode);
                    break;
            }

            secondsSwitch.setChecked(showSeconds);
            dayDateSwitch.setChecked(showDayDate);
        } catch(Throwable throwable) {
            // probably a called-from-wrong-thread-exception, we'll just ignore it
            Log.v(TAG, "ignoring exception while updating button state");
        }

        disableUICallbacks = false;
    }

    private void getFaceModeFromUI() {
        Log.v(TAG, "getFaceModeFromUI");
        int mode = -1;

        if(toolButton == null || numbersButton == null || liteButton == null || secondsSwitch == null || dayDateSwitch == null) {
            Log.v(TAG, "trying to get UI mode without buttons active yet");
            return;
        }

        if(toolButton.isChecked())
            mode = ClockState.FACE_TOOL;
        else if(numbersButton.isChecked())
            mode = ClockState.FACE_NUMBERS;
        else if(liteButton.isChecked())
            mode = ClockState.FACE_LITE;
        else Log.v(TAG, "no buttons are selected? weird.");

        boolean showSeconds = secondsSwitch.isChecked();
        boolean showDayDate = dayDateSwitch.isChecked();

        if(mode != -1) {
            getClockState().setFaceMode(mode);
        }
        getClockState().setShowSeconds(showSeconds);
        getClockState().setShowDayDate(showDayDate);

        getClockState().pingObservers(); // we only need to do this once, versus a whole bunch of times when it was happening internally
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "Create!");

    }

    private void activitySetup() {
        Log.v(TAG, "And in the beginning ...");

        setContentView(R.layout.activity_phone);

        // Core UI widgets: find 'em
        liteButton = (RadioButton) findViewById(R.id.liteButton);
        toolButton = (RadioButton) findViewById(R.id.toolButton);
        numbersButton = (RadioButton) findViewById(R.id.numbersButton);
        clockView = (MyViewAnim) findViewById(R.id.surfaceView);
        secondsSwitch = (Switch) findViewById(R.id.showSeconds);
        dayDateSwitch = (Switch) findViewById(R.id.showDayDate);
//        clockView.setSleepInEventLoop(true);

        Log.v(TAG, "registering callback");

        View.OnClickListener myListener = new View.OnClickListener() {
            public void onClick(View v) {
                if(!disableUICallbacks)
                    getFaceModeFromUI();
                if(clockView != null)
                    clockView.invalidate();
            }
        };

        liteButton.setOnClickListener(myListener);
        toolButton.setOnClickListener(myListener);
        numbersButton.setOnClickListener(myListener);
        secondsSwitch.setOnClickListener(myListener);
        dayDateSwitch.setOnClickListener(myListener);

        WatchCalendarService.kickStart(this);  // bring it up, if it's not already up

        PreferencesHelper.loadPreferences(this);

        onResume();
        Log.v(TAG, "activity setup complete");
    }

    protected void onPause() {
        super.onPause();
        Log.v(TAG, "Pause!");

        // perhaps incorrect assumption: if our activity is being killed, onStop will happen beforehand,
        // so we'll deregister our clockState observer, allowing this Activity object to become
        // garbage. A new one will be created if the activity ever comes back to life, which
        // will call getClockState(), which will in turn resurrect observer. Setting clockState=null
        // means that, even if this specific Activity object is resurrected from the dead, we'll
        // just reconnect it the next time somebody internally calls getClockState(). No harm, no foul.

        // http://developer.android.com/reference/android/app/Activity.html

        if (clockView != null)
            clockView.kill(this);

        getClockState().deleteObserver(this);
        clockState = null;
    }

    protected void onStart() {
        super.onStart();
        Log.v(TAG, "Start!");

        activitySetup();
    }

    protected void onResume() {
        super.onResume();
        Log.v(TAG, "Resume!");

        getClockState(); // side-effects: re-initializes observer

        if(clockView != null) {
            clockView.init(this);
            clockView.invalidate();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.phone, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void update(Observable observable, Object data) {
        // somebody changed *something* in the ClockState, causing us to get called
        Log.v(TAG, "Noticed a change in the clock state; saving preferences");
        setFaceModeUI(getClockState().getFaceMode(), getClockState().getShowSeconds(), getClockState().getShowDayDate());
        PreferencesHelper.savePreferences(this);
    }
}
