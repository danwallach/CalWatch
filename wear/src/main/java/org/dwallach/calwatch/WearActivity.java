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
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.Display;

import java.util.concurrent.locks.Lock;

public class WearActivity extends Activity {
    private static final String TAG = "WearActivity";

    private static WearActivity singletonActivity = null;
    private AlarmManager alarmManager;

    // This boolean keeps track of whether we think the watch is running or whether we think
    // the screen is completely dark. In the dark case, we'll keep all the alarms and intents
    // up and running but won't do any graphics at all. We *could* tear all of that down,
    // perhaps in hopes of saving a bit of power, but the wake-up-go-to-sleep cycle ensures
    // that we're not killed outright, which means we'll be around to receive messages from
    // the phone that update our service. This *might* not be necessary, since we've got
    // WearReceiverService running, hypothetically independent of the activity.

    // "volatile" added to this variable since the UI thread might be trying to shut things
    // down and we want the rendering thread to notice this ASAP.
    private volatile boolean watchFaceRunning = true;

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

        BatteryWrapper.init(this);

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

    private boolean alarmSet = false;

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "Resume!");
        // we're not taking any action here because this handled below by in onDisplayChanged
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG, "Pause!");
        // we're not taking any action here because this handled below by in onDisplayChanged
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.v(TAG, "Stop!");
        // we're not taking any action here because this handled below by in onDisplayChanged
    }

    private void startHelper() {
        Log.v(TAG, "startHelper: putting it all back together again");

        try {
            LockWrapper.lock();

            if(view == null) {
                Log.e(TAG, "no view yet, can't start things going");
                return;
            }

            initAlarm();

            if (displayListener == null) {
                initAmbientWatcher();
            }

            ClockFace clockFace = view.getClockFace();
            if (clockFace == null) {
                Log.e(TAG, "startHelper: no clock face!");
                return;
            }

            clockFace.setAmbientMode(false);
            view.setAmbientMode(false);
            view.redrawClock();                   // it might take a while for the other bits to get rolling again, so do this immediately
            view.resumeMaxHertz();
            watchFaceRunning = true;
        } finally {
            LockWrapper.unlock();
        }
    }

    private void stopHelper() {
        Log.v(TAG, "stopHelper: shutting things down");
        try {
            LockWrapper.lock();                       // locking, so we wait until redraw is finished
            watchFaceRunning = false;
            if (view != null) {
                view.stop();                          // kills the draw thread, if it's active
            } else {
                Log.e(TAG, "no view to stop?!");
            }
            killAlarms();
        } finally {
            LockWrapper.unlock();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // This happens when the user switches to a different watchface
        Log.v(TAG, "Destroy!");
        stopHelper();
        killAmbientWatcher();
    }

    private DisplayManager.DisplayListener displayListener = null;

    private void killAmbientWatcher() {
        Log.v(TAG, "killing ambient watcher");
        if (displayListener != null) {
            final DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            displayManager.unregisterDisplayListener(displayListener);
            displayListener = null;
        }
    }

    private void killAlarms() {
        Log.v(TAG, "killing alarms");
        if (pendingIntent != null) {
            alarmSet = false;
            alarmManager.cancel(pendingIntent);
            pendingIntent = null;
            alarmManager = null;
        }

        if (tickReceiver != null) {
            unregisterReceiver(tickReceiver);
            tickReceiver = null;
        }

        watchFaceRunning = false;
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
        if(view == null) {
            Log.e(TAG, "initAlarm: no view yet, can't initialize second-scale alarm");
            return;
        }
        if (alarmManager == null) {
            Log.v(TAG, "initAlarm: setting up");
            alarmManager = (AlarmManager) view.getContext().getSystemService(Context.ALARM_SERVICE);

            // every five seconds, we'll redraw the minute hand while sleeping; this gives us 12 ticks per minute, which should still look smooth
            // while otherwise saving lots of power
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, 5000, getPendingIntent());
            alarmSet = true;
        }

        if(!alarmSet) {
            Log.e(TAG, "initAlarm: failure");
        }
    }

    private void initAmbientWatcher() {
        Log.v(TAG, "initAmbientWatcher: starting");
        try {
            LockWrapper.lock();
            watchFaceRunning = true;

            // Create a broadcast receiver to handle change in time
            // Source: http://sourabhsoni.com/how-to-use-intent-action_time_tick/
            // Also: https://github.com/twotoasters/watchface-gears/blob/master/library/src/main/java/com/twotoasters/watchface/gears/widget/Watch.java

            // Note that we don't strictly need this stuff, since we're running a whole separate thread to do the graphics, but this
            // still serves a purpose. If that thread isn't working, this will still work and we'll get at least *some* updates
            // on the screen, albeit far less frequent.
            if (tickReceiver == null) {
                Log.v(TAG, "initializing tickReceiver");
                tickReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String actionString = intent.getAction();

                        if (!watchFaceRunning) {
                            // received a timer event but we're not supposed to be doing graphics right now,
                            // so just quietly return

                            return;
                        }

                        if (actionString.equals(Intent.ACTION_TIME_CHANGED) || actionString.equals(Intent.ACTION_TIME_TICK)) {
                            if (view == null) {
                                Log.v(TAG, actionString + " received, but can't redraw");
                            } else {
//                            Log.v(TAG, actionString + " received, redrawing");
                                view.redrawClock();
                            }
                            initAlarm(); // just in case it's not set up properly
                        } else if (actionString.equals(ACTION_KEEP_WATCHFACE_AWAKE)) {
//                        Log.v(TAG, "five second alarm!");
                            if (view != null) {
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

            if (displayListener == null) {
                Log.v(TAG, "initializing displayListener");

                Handler handler = new Handler(Looper.getMainLooper());

                final DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);

                displayListener = new DisplayManager.DisplayListener() {
                    private int oldState = -1;

                    @Override
                    public void onDisplayAdded(int displayId) {
                    }

                    @Override
                    public void onDisplayRemoved(int displayId) {
                        // https://gist.github.com/kentarosu/52fb21eb92181716b0ce suggests that we pay attention here
                        Log.v(TAG, "Display removed, shutting down");
                        stopHelper();
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

                        if (oldState == newState) {
                            Log.v(TAG, "state didn't actually change; ignoring");
                            return;
                        }

                        if (oldState == Display.STATE_DOZING || oldState == Display.STATE_ON) {
                            // if we'd been previously running for a while, then we're about to change things up,
                            // so now's a good time to report our status
                            TimeWrapper.frameReport();
                        } else {
                            // we were formerly asleep and now we're coming back awake again
                            TimeWrapper.frameReset();
                        }
                        oldState = newState;


                        // inspiration: https://gist.github.com/kentarosu/52fb21eb92181716b0ce

                        switch (newState) {
                            case Display.STATE_DOZING:
                                Log.v(TAG, "onDisplayChanged: dozing");
                                clockFace.setAmbientMode(true);
                                view.setAmbientMode(true);
                                view.stop();                          // stops the drawing thread
                                view.redrawClock();                   // it might take a while for the other bits to get rolling again, so do this immediately
                                watchFaceRunning = true;
                                break;

                            case Display.STATE_OFF:

                                // Curiously, this case never executes when we're in *ambient mode*, at least on the Moto360

                                Log.v(TAG, "onDisplayChanged: off!");
                                stopHelper();                         // stopHelper will set watchFaceRunning to false, so we don't need to do anything here
                                clockFace.setAmbientMode(false);
                                view.setAmbientMode(false);
                                break;

                            default:
                                Log.v(TAG, "unknown state (" + newState + "), treating as \"on\" state");
                                // pass through

                            case Display.STATE_ON:
                                Log.v(TAG, "onDisplayChanged: on!");
                                startHelper();
                                break;
                        }
                    }
                };
                displayManager.registerDisplayListener(displayListener, handler);
            }
        } finally {
            LockWrapper.unlock();
            Log.v(TAG, "initAmbientWatcher: complete");
        }
    }
}
