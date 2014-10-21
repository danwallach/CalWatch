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

import java.util.Observable;
import java.util.Observer;

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
        if (view != null) view.resume();
        initAmbientWatcher();
        initAlarm();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG, "Pause!");
        // we're not taking any action here because this handled below by in onDisplayChanged
        // if(view != null) view.pause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.v(TAG, "Stop!");
        
        // originally, we took no action here, since we were going to handle it below with onDisplayChanged
        // but this occasionally causes an exception to be thrown (and mysteriously caught) within the Looper
        // as below:

//        10-15 16:19:52.084  13774-15502/? W/MessageQueue﹕ Handler (android.view.Choreographer$FrameHandler) {adcfe8a0} sending message to a Handler on a dead thread
//        java.lang.RuntimeException: Handler (android.view.Choreographer$FrameHandler) {adcfe8a0} sending message to a Handler on a dead thread
//        at android.os.MessageQueue.enqueueMessage(MessageQueue.java:320)
//        at android.os.Handler.enqueueMessage(Handler.java:626)
//        at android.os.Handler.sendMessageAtTime(Handler.java:595)
//        at android.view.Choreographer$FrameDisplayEventReceiver.onVsync(Choreographer.java:741)
//        at android.view.DisplayEventReceiver.dispatchVsync(DisplayEventReceiver.java:139)
//        at android.os.MessageQueue.nativePollOnce(Native Method)
//        at android.os.MessageQueue.next(MessageQueue.java:138)
//        at android.os.Looper.loop(Looper.java:123)
//        at org.dwallach.calwatch.MyViewAnim$PanelThread.run(MyViewAnim.java:402)
//        10-15 16:19:52.084  13774-15502/? V/MyViewAnim﹕ looper finished!

        // We tried doing all the stop-related activity here, but this only caused the exception as above to
        // happen *more* often. The solution seems to be doing nothing here and doing everything with the
        // onDisplayChanged callback. The other thing that seemed to help was making watchFaceRunning into
        // a boolean, so it's going to be noticed immediately on every path through the rendering loop.

//        stopHelper();
    }

    private void stopHelper() {
        Log.v(TAG, "stopHelper: shutting things down");
        watchFaceRunning = false;
        if(view != null) {
            view.stop();                          // kills the draw thread, if it's active
        } else {
            Log.e(TAG, "no view to stop?!");
        }
        killAmbientWatcher();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Hmm... this never seems to actually happen
        Log.v(TAG, "Destroy!");
        stopHelper();
    }

    private DisplayManager.DisplayListener displayListener = null;

    private void killAmbientWatcher() {
        Log.v(TAG, "killing ambient watcher & alarm");
//        if (displayListener != null) {
//            final DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
//            displayManager.unregisterDisplayListener(displayListener);
//            displayListener = null;
//        }

        if(pendingIntent != null) {
            alarmSet = false;
            alarmManager.cancel(pendingIntent);
            pendingIntent = null;
            alarmManager = null;
        }

        if(tickReceiver != null) {
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
        watchFaceRunning = true;

        // Create a broadcast receiver to handle change in time
        // Source: http://sourabhsoni.com/how-to-use-intent-action_time_tick/
        // Also: https://github.com/twotoasters/watchface-gears/blob/master/library/src/main/java/com/twotoasters/watchface/gears/widget/Watch.java

        // Note that we don't strictly need this stuff, since we're running a whole separate thread to do the graphics, but this
        // still serves a purpose. If that thread isn't working, this will still work and we'll get at least *some* updates
        // on the screen, albeit far less frequent.
        if(tickReceiver == null) {
            tickReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String actionString = intent.getAction();

                    if(!watchFaceRunning) {
                        // received a timer event but we're not supposed to be doing graphics right now,
                        // so just quietly return

                        return;
                    }

                    if(actionString.equals(Intent.ACTION_TIME_CHANGED) || actionString.equals(Intent.ACTION_TIME_TICK)) {
                        if(view == null) {
                            Log.v(TAG, actionString + " received, but can't redraw");
                        } else {
//                            Log.v(TAG, actionString + " received, redrawing");
                            view.redrawClock();
                        }
                        initAlarm(); // just in case it's not set up properly
                    } else if(actionString.equals(ACTION_KEEP_WATCHFACE_AWAKE)) {
//                        Log.v(TAG, "five second alarm!");
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
                private int oldState = -1;

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

                    if(oldState == Display.STATE_DOZING || oldState == Display.STATE_ON) {
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

                        case Display.STATE_ON:
                            Log.v(TAG, "onDisplayChanged: on!");
                            clockFace.setAmbientMode(false);
                            view.setAmbientMode(false);
                            view.redrawClock();                   // it might take a while for the other bits to get rolling again, so do this immediately
                            view.resume();
                            watchFaceRunning = true;
                            break;

                        default:

                            // never seen this happen, ever

                            Log.v(TAG, "onDisplayChanged: unknown state, defaulting to non-ambient mode");
                            clockFace.setAmbientMode(false);
                            view.setAmbientMode(false);
                            view.resume();
                            watchFaceRunning = true;
                            break;
                    }
                }
            };
            displayManager.registerDisplayListener(displayListener, handler);
        }
    }
}
