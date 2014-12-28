/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.CalendarContract;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;

import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.TimeUnit;

public class MyViewAnim extends SurfaceView implements Observer {
    private static final String TAG = "MyViewAnim";

    public MyViewAnim(Context context, AttributeSet attrs) {
        super(context, attrs);

        setWillNotDraw(false);
        init(context);
    }

    /**
     * Update rate in milliseconds for NON-interactive mode. We update once every 12 seconds
     * to advance the minute hand when we're not otherwise sweeping the second hand.
     *
     * The default of one update per minute is ugly so we'll do better.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(12);

    private static final int MSG_UPDATE_TIME = 0;

    private ClockFace clockFace;
    private ClockState clockState;


    private boolean visible = false;

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        visible = (visibility == VISIBLE);

        if(!visible)
            TimeWrapper.frameReport();
        else {
            TimeWrapper.frameReset();
            updateTimer();
            invalidate();
        }
    }

    /**
     * Handler to tick once every 12 seconds.
     * Used only if the second hand is turned off
     * or in ambient mode so we get smooth minute-hand
     * motion.
     */
    private final Handler mUpdateTimeHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case MSG_UPDATE_TIME:
//                        Log.v(TAG, "updating time");

                    invalidate();
                    if (shouldTimerBeRunning()) {
                        long timeMs = System.currentTimeMillis();
                        long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                        mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                    }
                    break;
            }
        }
    };

    private boolean shouldTimerBeRunning() {
        boolean timerNeeded = false;

        if(!visible)
            return false;

        // run the timer if we're in ambient mode
        if(clockFace != null) {
            timerNeeded = clockFace.getAmbientMode();
        }

        // or run the timer if we're not showing the seconds hand
        if(clockState != null) {
            timerNeeded = timerNeeded || !clockState.getShowSeconds();
        }

        return timerNeeded;
    }

    /**
     * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
     * or stops it if it shouldn't be running but currently is.
     */
    private void updateTimer() {
        Log.d(TAG, "updateTimer");

        mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
        if (shouldTimerBeRunning()) {
            mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
        }
    }

    /**
     * Callback from ClockState if something changes, which means we'll need
     * to redraw.
     * @param observable
     * @param data
     */
    @Override
    public void update(Observable observable, Object data) {
        invalidate();
    }

    private CalendarFetcher calendarFetcher;

    public void init(Context context) {
        Log.d(TAG, "init");

        // announce our version number to the logs
        VersionWrapper.logVersion(context);

        BatteryWrapper.init(context);
        Resources resources = context.getResources();

        if (resources == null) {
            Log.e(TAG, "no resources? not good");
        }

        clockFace = new ClockFace();
        clockState = ClockState.getSingleton();

        clockState.addObserver(this); // callbacks if something changes
        calendarFetcher = new CalendarFetcher(context, CalendarContract.Instances.CONTENT_URI);


    }

    public void kill(Context context) {
        mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);

        if(calendarFetcher != null)
            calendarFetcher.kill();
    }


    private long drawCounter = 0;

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
        Log.v(TAG, "size change: " + w + ", " + h);
        this.width = w;
        this.height = h;
    }

    private int width, height;


    @Override
    public void onDraw(Canvas canvas) {
        drawCounter++;

        if(width == 0 || height == 0) {
            if(drawCounter % 1000 == 1)
                Log.e(TAG, "zero-width or zero-height, can't draw yet");
            return;
        }

        try {
            // clear the screen
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            clockFace.setSize(width, height);

            TimeWrapper.update(); // fetch the time
            clockFace.drawEverything(canvas);
        } catch (Throwable t) {
            if(drawCounter % 1000 == 0)
                Log.e(TAG, "Something blew up while drawing", t);
        }

        // Draw every frame as long as we're visible and doing the sweeping second hand,
        // otherwise the timer will take care of it.
        if (!shouldTimerBeRunning())
            invalidate();
    }

}


