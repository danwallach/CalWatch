package org.dwallach.calwatch;
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
    private static PhoneActivity theActivity;

    private Switch toggle;
    private RadioButton toolButton, numbersButton, liteButton;
    private CalendarFetcher fetcher = null;
    private ClockFaceStub clockFace = new ClockFaceStub();

    public static PhoneActivity getSingletonActivity() {
        return theActivity;
    }

    public void savePreferences() {
        Log.v("PhoneActivity", "savePreferences");
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean("showSeconds", clockFace.getShowSeconds());
        editor.putInt("faceMode", clockFace.getFaceMode());

        if(!editor.commit())
            Log.v("PhoneActivity", "savePreferences commit failed ?!");
    }

    public void loadPreferences() {
        Log.v("PhoneActivity", "loadPreferences");

        if(clockFace == null) {
            Log.v("PhoneActivity", "loadPreferences has no clock to put them in");
            return;
        }

        if(toggle == null || toolButton == null || numbersButton == null || liteButton == null) {
            Log.v("PhoneActivity", "loadPreferences has no widgets to update");
            return;
        }

        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        boolean showSeconds = prefs.getBoolean("showSeconds", true);
        int faceMode = prefs.getInt("faceMode", ClockFaceStub.FACE_TOOL);

        clockFace.setFaceMode(faceMode);
        clockFace.setShowSeconds(showSeconds);
        toggle.setChecked(showSeconds);
        setFaceModeUI(faceMode);
    }

    CalendarFetcher getFetcher() {
        return fetcher;
    }

    //
    // this will be called, eventually, from whatever feature is responsible for
    // restoring saved user preferences
    //
    private void setFaceModeUI(int mode) {
        if(toolButton == null || numbersButton == null || liteButton == null) {
            Log.v("PhoneActivity", "trying to set face mode without buttons active yet");
            return;
        }

        switch(mode) {
            case ClockFaceStub.FACE_TOOL:
                toolButton.performClick();
                break;
            case ClockFaceStub.FACE_NUMBERS:
                numbersButton.performClick();
                break;
            case ClockFaceStub.FACE_LITE:
                liteButton.performClick();
                break;
            default:
                Log.v("PhoneActivity", "bogus face mode: " + mode);
                break;
        }
    }

    private void getFaceModeFromUI() {
        int mode = -1;

        if(toolButton == null || numbersButton == null || liteButton == null) {
            Log.v("PhoneActivity", "trying to set face mode without buttons active yet");
            return;
        }

        if(toolButton.isChecked())
            mode = ClockFaceStub.FACE_TOOL;
        else if(numbersButton.isChecked())
            mode = ClockFaceStub.FACE_NUMBERS;
        else if(liteButton.isChecked())
            mode = ClockFaceStub.FACE_LITE;
        else Log.v("PhoneActivity", "no buttons are selected? weird.");

        if(mode != -1 && clockFace != null) {
            clockFace.setFaceMode(mode);
            savePreferences();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activitySetup();

    }

    public void setClockFace(ClockFaceStub clockFace) {
        this.clockFace = clockFace;
    }

    public ClockFaceStub getClockFace() {
        return clockFace;
    }

    private void activitySetup() {
        textOut("And in the beginning ...");
        theActivity = this;

        setContentView(R.layout.activity_phone);

        // Core UI widgets: find 'em
        toggle = (Switch)findViewById(R.id.toggleButton);
        liteButton = (RadioButton) findViewById(R.id.liteButton);
        toolButton = (RadioButton) findViewById(R.id.toolButton);
        numbersButton = (RadioButton) findViewById(R.id.numbersButton);

        textOut("registering callback");

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

        if(fetcher != null) {
            Log.w("PhoneActivity", "whoa, multiple calendar fetchers!?");
        } else {
            textOut("starting fetcher");
            fetcher = new CalendarFetcher(this);
        }
    }

    protected void onStop() {
        super.onStop();
        if(fetcher != null) fetcher.haltUpdates();
        textOut("Stop!");
    }

    protected void onStart() {
        super.onStart();
        if(fetcher != null) fetcher.resumeUpdates();
        textOut("Start!");
    }

    protected void onResume() {
        super.onResume();

        if(this != theActivity) {
            textOut("Resuming on new activity!");
            activitySetup();
        }

        textOut("Resume!");
    }

    protected void onPause() {
        super.onPause();
        if(fetcher != null) fetcher.haltUpdates();

        textOut("Pause!");
    }

    public static void textOut(String text) {
        Log.v("PhoneActivity", text);

        // there used to be a textView, but it's dead, so this method is now vestigal
    }

    // when the user clicks the button
    protected void showSecondsStateChange(boolean state) {
        textOut(state?"Selected":"Unselected");

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
