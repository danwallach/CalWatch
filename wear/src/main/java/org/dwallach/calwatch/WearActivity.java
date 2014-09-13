package org.dwallach.calwatch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

public class WearActivity extends Activity {
    private static final String TAG = "WearActivity";

    private static WearActivity singletonActivity = null;

    public static WearActivity getSingletonActivity() {
        return singletonActivity;
    }

    private MyViewAnim view;

    public MyViewAnim getViewAnim() { return view; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "starting onCreate");

        singletonActivity = this;

        // start the background service, if it's not already running
        WearReceiverService.kickStart(this);

        // bring up the UI
        setContentView(R.layout.activity_wear);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                view = (MyViewAnim) stub.findViewById(R.id.surfaceView);

                // this would keep the screen on: useful for testing but not something
                // we want for production use

                // getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


                Log.v(TAG, "starting data API receiver");
            }
        });

        initAmbientWatcher();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "Resume!");
//        if(view != null) view.resume();
        initAmbientWatcher();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG, "Pause!");
//        if(view != null) view.pause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.v(TAG, "Stop!");
//        if(view != null) view.stop();
//        killAmbientWatcher();
    }

    private DisplayManager.DisplayListener displayListener = null;

    private void killAmbientWatcher() {
        Log.v(TAG, "killing ambient watcher");
        if(displayListener != null) {
            final DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            displayManager.unregisterDisplayListener(displayListener);
            displayListener = null;
        }
    }

    private void initAmbientWatcher() {
        if(this.displayListener == null) {
            Log.v(TAG, "initializing ambient watcher");

            Handler handler = new Handler(Looper.getMainLooper());

            final DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);

            this.displayListener = new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    int newState = displayManager.getDisplay(displayId).getState();
                    Log.v(TAG, "display changed: " + displayId + ", new state: " + newState);

                    ClockFace clockFace = view.getClockFace();

                    if (clockFace == null) {
                        Log.v(TAG, "no clockFace! not good");
                        return;
                    }

                    // inspiration: https://gist.github.com/kentarosu/52fb21eb92181716b0ce

                    switch (newState) {
                        case Display.STATE_DOZING:
                            clockFace.setAmbientMode(true);
                            Log.v(TAG, "onDisplayChanged: dozing");
                            break;
                        case Display.STATE_OFF:
                            clockFace.setAmbientMode(true);
                            Log.v(TAG, "onDisplayChanged: off!"); // presumably this event will be accompanied by other things that shut us down
                            break;
                        case Display.STATE_ON:
                            clockFace.setAmbientMode(false);
                            Log.v(TAG, "onDisplayChanged: on!");
                            break;
                        default:
                            clockFace.setAmbientMode(false);
                            Log.v(TAG, "onDisplayChanged: unknown state, defaulting to non-ambient mode");
                            break;
                    }
                }
            };
            displayManager.registerDisplayListener(displayListener, handler);
        }
    }
}
