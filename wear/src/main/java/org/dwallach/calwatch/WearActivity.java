package org.dwallach.calwatch;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WearActivity extends Activity {
    private static final String TAG = "WearActivity";

    private static WearActivity singletonActivity = null;
    private AlarmManager alarmManager;

    public static WearActivity getSingletonActivity() {
        return singletonActivity;
    }

    private MyViewAnim view;

    public MyViewAnim getViewAnim() {
        return view;
    }

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

    boolean alarmSet = false;

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "Resume!");
        if (view != null) view.resume();
        initAmbientWatcher();
        initAlarm();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "Destroy!");
        if (view != null) view.stop();
        killAmbientWatcher();
    }

    private DisplayManager.DisplayListener displayListener = null;

    private void killAmbientWatcher() {
        Log.v(TAG, "killing ambient watcher & alarm");
        if (displayListener != null) {
            final DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            displayManager.unregisterDisplayListener(displayListener);
            displayListener = null;
        }

        if(pendingIntent != null) {
            alarmSet = false;
            alarmManager.cancel(pendingIntent);
            pendingIntent = null;
        }

    }

    private BroadcastReceiver tickReceiver = null;
    private static final String ACTION_KEEP_WATCHFACE_AWAKE = "intent.action.keep.watchface.awake";
    private PendingIntent pendingIntent = null;

    private PendingIntent getPendingIntent() {
        if(pendingIntent == null && view != null)
            pendingIntent =  PendingIntent.getBroadcast(view.getContext(), 0, new Intent(ACTION_KEEP_WATCHFACE_AWAKE), 0);
        return pendingIntent;
    }

    private void initAlarm() {
        if (alarmManager == null && view != null) {
            Log.v(TAG, "initializing second-scale alarm");
            alarmManager = (AlarmManager) view.getContext().getSystemService(Context.ALARM_SERVICE);

            // every five seconds, we'll redraw the minute hand while sleeping; this gives us 12 ticks per minute, which should still look smooth
            // while otherwise saving lots of power
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, 5000, getPendingIntent());
            alarmSet = true;
        }

        if(!alarmSet) {
            Log.e(TAG, "failed to initialize alarm");
        }
    }

    private void initAmbientWatcher() {

        // Create a broadcast receiver to handle change in time
        // Source: http://sourabhsoni.com/how-to-use-intent-action_time_tick/
        // Also: https://github.com/twotoasters/watchface-gears/blob/master/library/src/main/java/com/twotoasters/watchface/gears/widget/Watch.java
        if(tickReceiver == null) {
            tickReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String actionString = intent.getAction();
                    if(actionString.equals(Intent.ACTION_TIME_CHANGED) || actionString.equals(Intent.ACTION_TIME_TICK)) {
                        if(view == null) {
                            Log.v(TAG, actionString + " received, but can't redraw");
                        } else {
                            Log.v(TAG, actionString + " received, redrawing");
                            view.redrawClock();
                        }
                        initAlarm(); // just in case it's not set up properly
                    } else if(actionString.equals(ACTION_KEEP_WATCHFACE_AWAKE)) {
                        Log.v(TAG, "five second alarm!");
                        if(view != null) {
                            view.redrawClock();
                        } else {
                            Log.e(TAG, "tick received, no clock view to redraw");
                        }
                    } else {
                        Log.e(TAG, "Unknown intent received: " + intent.toString());
                    }
                }
            };

            //Register the broadcast receiver to receive TIME_TICK
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(ACTION_KEEP_WATCHFACE_AWAKE);

            registerReceiver(tickReceiver, filter);
        }

        if (this.displayListener == null) {
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
