package org.dwallach.calwatch;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Switch;

import java.util.Observable;
import java.util.Observer;


public class PhoneActivity extends Activity implements Observer {
    private final static String TAG = "PhoneActivity";

    private RadioButton toolButton, numbersButton, liteButton;
    private MyViewAnim clockView;

    private ClockState clockState;

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
    public void setFaceModeUI(int mode) {
        if(toolButton == null || numbersButton == null || liteButton == null) {
            Log.v(TAG, "trying to set face mode without buttons active yet");
            return;
        }

        switch(mode) {
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
    }

    private void getFaceModeFromUI() {
        int mode = -1;

        if(toolButton == null || numbersButton == null || liteButton == null) {
            Log.v(TAG, "trying to set face mode without buttons active yet");
            return;
        }

        if(toolButton.isChecked())
            mode = ClockState.FACE_TOOL;
        else if(numbersButton.isChecked())
            mode = ClockState.FACE_NUMBERS;
        else if(liteButton.isChecked())
            mode = ClockState.FACE_LITE;
        else Log.v(TAG, "no buttons are selected? weird.");

        if(mode != -1) {
            getClockState().setFaceMode(mode);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "Create!");

    }

    private void activitySetup() {
        Log.v(TAG, "And in the beginning ...");

        getClockState(); // initialize it, if it's not already here

        BatteryMonitor.init(this);

        setContentView(R.layout.activity_phone);

        // Core UI widgets: find 'em
        liteButton = (RadioButton) findViewById(R.id.liteButton);
        toolButton = (RadioButton) findViewById(R.id.toolButton);
        numbersButton = (RadioButton) findViewById(R.id.numbersButton);
        clockView = (MyViewAnim) findViewById(R.id.surfaceView);
        clockView.setSleepInEventLoop(true);

        Log.v(TAG, "registering callback");

        liteButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                getFaceModeFromUI();
            }
        });

        toolButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                getFaceModeFromUI();
            }
        });

        numbersButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                getFaceModeFromUI();
            }
        });

        WatchReceiver.kickStart(this);        // bring it up, if it's not already up
        WatchCalendarService.kickStart(this); // bring it up, if it's not already up

        loadPreferences();
    }

    protected void onStop() {
        super.onStop();
        Log.v(TAG, "Stop!");

        // perhaps incorrect assumption: if our activity is being killed, onStop will happen beforehand,
        // so we'll deregister our clockState observer, allowing this Activity object to become
        // garbage. A new one will be created if the activity ever comes back to life, which
        // will call getClockState(), which will in turn resurrect observer. Setting clockState=null
        // means that, even if this specific Activity object is resurrected from the dead, we'll
        // just reconnect it the next time somebody internally calls getClockState(). No harm, no foul.

        // http://developer.android.com/reference/android/app/Activity.html

        if(clockView != null) clockView.stop();

        clockState.deleteObserver(this);
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
        if(clockView != null) clockView.resume(); // shouldn't be necessary, but isn't happening on its own
    }

    protected void onPause() {
        super.onPause();
        Log.v(TAG, "Pause!");
        if(clockView != null) clockView.pause(); // shouldn't be necessary, but isn't happening on its own
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.phone, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void savePreferences() {
        Log.v(TAG, "savePreferences");
        SharedPreferences prefs = getSharedPreferences("org.dwallach.calwatch.prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        ClockState clockState = getClockState();
        editor.putInt("faceMode", clockState.getFaceMode());

        if(!editor.commit())
            Log.v(TAG, "savePreferences commit failed ?!");
    }

    public void loadPreferences() {
        Log.v(TAG, "loadPreferences");

        ClockState clockState = getClockState();

        SharedPreferences prefs = getSharedPreferences("org.dwallach.calwatch.prefs", MODE_PRIVATE);
        int faceMode = prefs.getInt("faceMode", ClockState.FACE_TOOL);

        clockState.setFaceMode(faceMode);

        if (toolButton == null || numbersButton == null || liteButton == null) {
            Log.v(TAG, "loadPreferences has no widgets to update");
            return;
        }

        setFaceModeUI(faceMode);
    }

    @Override
    public void update(Observable observable, Object data) {
        // somebody changed *something* in the ClockState, causing us to get called
        Log.v(TAG, "Noticed a change in the clock state; saving preferences");
        savePreferences();
    }
}
