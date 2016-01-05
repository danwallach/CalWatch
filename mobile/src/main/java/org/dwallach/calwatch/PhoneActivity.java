/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;
import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioButton;
import android.widget.Switch;

import java.lang.ref.WeakReference;
import java.util.Observable;
import java.util.Observer;


public class PhoneActivity extends Activity implements Observer {
    private final static String TAG = "PhoneActivity";

    private RadioButton toolButton, numbersButton, liteButton;
    private MyViewAnim clockView;
    private Switch secondsSwitch, dayDateSwitch;

    private ClockState clockState;
    private boolean disableUICallbacks = false;

    private static WeakReference<Activity> activityRef;

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
        activityRef = new WeakReference<Activity>(this);
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

        setContentView(R.layout.activity_phone);
    }

    @Override
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

        if(clockState != null) {
            clockState.deleteObserver(this);
            clockState = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "Start!");

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
        CalendarPermission.init(this);

        clockView.init(this);
        clockView.initCalendarFetcher(this);

        onResume();
        Log.v(TAG, "activity setup complete");
    }

    @Override
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        Log.v(TAG, "onRequestPermissionsResult");
        CalendarPermission.handleResult(requestCode, permissions, grantResults);
//        CalWatchFaceService.Engine engine = CalWatchFaceService.getEngine();
//        if(engine != null) {
//            engine.calendarPermissionUpdate();
//        }
        Log.v(TAG, "finishing PermissionActivity");
    }

    /**
     * This will be called when the user clicks on the watchface, presumably because they want
     * us to request calendar permissions.
     */
    static void watchfaceClick(MyViewAnim view) {
        Log.v(TAG, "Watchface clicked!");
        if(activityRef == null)
            return; // can't do anything without an activity

        Activity activity = activityRef.get();
        if(activity == null)
            return; // can't do anything with an activity

        ClockState clockState = ClockState.getSingleton();
        if(clockState == null)
            Log.v(TAG, "Activity found and clockState is null.");
        else if(!clockState.getCalendarPermission()) {
            Log.v(TAG, "Requesting permissions");
            CalendarPermission.request(activity);
            view.initCalendarFetcher(activity);
        } else {
            Log.v(TAG, "Permissions already granted.");
        }
    }
}
