/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class PreferencesHelper {
    private final static String TAG = "PreferencesHelper";

    public static void savePreferences(Context context) {
        Log.v(TAG, "savePreferences");
        SharedPreferences prefs = context.getSharedPreferences("org.dwallach.calwatch.prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        ClockState clockState = ClockState.getSingleton();
        if(clockState == null) {
            Log.e(TAG, "no clock state yet, can't save preferences");
            return;
        }

        editor.putInt("faceMode", clockState.getFaceMode());
        editor.putBoolean("showSeconds", clockState.getShowSeconds());
        editor.putBoolean("showDayDate", clockState.getShowDayDate());

        if(!editor.commit())
            Log.e(TAG, "savePreferences commit failed ?!");
    }

    public static void loadPreferences(Context context) {
        Log.v(TAG, "loadPreferences");

        ClockState clockState = ClockState.getSingleton();
        if(clockState == null) {
            Log.e(TAG, "no clock state yet, can't load preferences");
            return;
        }


        SharedPreferences prefs = context.getSharedPreferences("org.dwallach.calwatch.prefs", Context.MODE_PRIVATE);
        int faceMode = prefs.getInt("faceMode", Constants.DefaultWatchFace); // ClockState.FACE_TOOL
        boolean showSeconds = prefs.getBoolean("showSeconds", Constants.DefaultShowSeconds);
        boolean showDayDate = prefs.getBoolean("showDayDate", Constants.DefaultShowDayDate);

        Log.v(TAG, "faceMode: " + faceMode + ", showSeconds: " + showSeconds + ", showDayDate: " + showDayDate);

        clockState.setFaceMode(faceMode);
        clockState.setShowSeconds(showSeconds);
        clockState.setShowDayDate(showDayDate);

        clockState.pingObservers(); // we only need to do this once, versus multiple times when done internally
    }
}
