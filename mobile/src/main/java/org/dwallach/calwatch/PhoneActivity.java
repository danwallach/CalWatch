package org.dwallach.calwatch;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Switch;


import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Switch;


public class PhoneActivity extends Activity {
    private final static String TAG = "PhoneActivity";

    private static PhoneActivity theActivity;

    Switch toggle;
    RadioButton toolButton, numbersButton, liteButton;
    private ClockFaceStub clockFace;

    public static PhoneActivity getSingletonActivity() {
        return theActivity;
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
            case ClockFace.FACE_TOOL:
                toolButton.performClick();
                break;
            case ClockFace.FACE_NUMBERS:
                numbersButton.performClick();
                break;
            case ClockFace.FACE_LITE:
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
            mode = ClockFace.FACE_TOOL;
        else if(numbersButton.isChecked())
            mode = ClockFace.FACE_NUMBERS;
        else if(liteButton.isChecked())
            mode = ClockFace.FACE_LITE;
        else Log.v(TAG, "no buttons are selected? weird.");

        if(mode != -1 && clockFace != null) {
            clockFace.setFaceMode(mode);
            savePreferences();
        }
    }

    private void fetchClockFace() {
        final MyViewAnim animView = (MyViewAnim) findViewById(R.id.surfaceView);
        if(animView != null) {
            Log.v(TAG, "Getting real clock face");
            this.clockFace = animView.getClockFace();
        } else {
            Log.v(TAG, "No MyViewAnim -> fake clock face");
            this.clockFace = new ClockFaceStub();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activitySetup();

    }

    public void savePreferences() {
        WatchCalendarService service = WatchCalendarService.getSingletonService();
        if(service != null)
            service.savePreferences();
    }

    public ClockFaceStub getClockFace() {
        if(clockFace == null)
            fetchClockFace();
        return clockFace;
    }

    private void activitySetup() {
        Log.v(TAG, "And in the beginning ...");
        theActivity = this;

        setContentView(R.layout.activity_phone);

        // Core UI widgets: find 'em
        toggle = (Switch)findViewById(R.id.toggleButton);
        liteButton = (RadioButton) findViewById(R.id.liteButton);
        toolButton = (RadioButton) findViewById(R.id.toolButton);
        numbersButton = (RadioButton) findViewById(R.id.numbersButton);

        Log.v(TAG, "registering callback");

        // Register the onClick listener for the seconds? button
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                showSecondsStateChange(isChecked);
            }
        });

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

        // start the calendar service, if it's not already running
        WatchCalendarService watchCalendarService = WatchCalendarService.getSingletonService();

        if(watchCalendarService == null) {
            Intent serviceIntent = new Intent(this, WatchCalendarService.class);
            startService(serviceIntent);

            // do it again; we should get something different this time
            watchCalendarService = WatchCalendarService.getSingletonService();
        }

        fetchClockFace();
    }

    protected void onStop() {
        super.onStop();
        Log.v(TAG, "Stop!");
    }

    protected void onStart() {
        super.onStart();
        Log.v(TAG, "Start!");
    }

    protected void onResume() {
        super.onResume();

        if(this != theActivity) {
            Log.v(TAG, "Resuming on new activity!");
            activitySetup();
        }

        Log.v(TAG, "Resume!");
    }

    protected void onPause() {
        super.onPause();
        Log.v(TAG, "Pause!");
    }

    // when the user clicks the button
    protected void showSecondsStateChange(boolean state) {
        Log.v(TAG, state?"Selected":"Unselected");

        if(clockFace != null) {
            clockFace.setShowSeconds(state);
            savePreferences();
        }
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
}
