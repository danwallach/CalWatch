/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.provider.CalendarContract;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.Observable;
import java.util.Observer;

public class MyViewAnim extends View implements Observer {
    private static final String TAG = "MyViewAnim";

    public MyViewAnim(Context context, AttributeSet attrs) {
        super(context, attrs);

        setWillNotDraw(false);
        init(context);
    }

    private ClockFace clockFace;
    private ClockState clockState;


    private boolean visible = false;

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        visible = (visibility == VISIBLE);

        if(!visible)
            TimeWrapper.frameReport();
        else {
            TimeWrapper.frameReset();
            invalidate();
        }
    }

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
     * Callback from ClockState if something changes, which means we'll need
     * to redraw.
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

        if(clockFace == null) {
            clockFace = new ClockFace();
            Bitmap emptyCalendar = BitmapFactory.decodeResource(context.getResources(), R.drawable.empty_calendar);
            clockFace.setMissingCalendarBitmap(emptyCalendar);

            clockFace.setSize(width, height);
        }

        clockState = ClockState.getSingleton();

        clockState.deleteObserver(this); // in case we were already there
        clockState.addObserver(this); // ensure we're there, but only once

//        initCalendarFetcher(context);
    }

    // this is redundant with CalWatchFaceService.Engine.initCalendarFetcher, but just different enough that it's
    // not really worth trying to have a grand unified thing.
    public void initCalendarFetcher(Activity activity) {
        Log.v(TAG, "initCalendarFetcher");
        if(calendarFetcher != null) {
            calendarFetcher.kill();
            calendarFetcher = null;
        }

        boolean permissionGiven = CalendarPermission.check(activity);
        if(!clockState.getCalendarPermission() && permissionGiven) {
            // Hypothetically this isn't necessary, because it's handled in CalendarPermission.handleResult.
            // Nonetheless, paranoia.
            Log.e(TAG, "we've got permission, need to update the clockState");
            clockState.setCalendarPermission(true);
        }

        if(!permissionGiven) {
            Log.v(TAG, "no calendar permission given, requesting if we haven't requested beforehand");
            CalendarPermission.requestFirstTimeOnly(activity);
            return;
        }

        calendarFetcher = new CalendarFetcher(activity, CalendarContract.Instances.CONTENT_URI, CalendarContract.AUTHORITY);
    }


    // when the user started up the config panel, then navigated away and came back, but apparently
    // only under Android 5.0, it would tear down and restart everything, but onSizeChanged() would
    // never actually happen the second time around. This is the workaround.

    // credit where due:
    // http://www.sherif.mobi/2013/01/how-to-get-widthheight-of-view.html
    // http://stackoverflow.com/questions/10411975/how-to-get-the-width-and-height-of-an-image-view-in-android
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        int tmpWidth = getWidth();
        int tmpHeight = getHeight();

        if(tmpWidth == 0 || tmpHeight == 0) {
            Log.v(TAG, "onWindowFocusChanged: got zeros for width or height");
            return;
        }

        this.width = tmpWidth;
        this.height = tmpHeight;

        Log.v(TAG, "onWindowFocusChanged: " + width + ", " + height);
        if(clockFace != null)
            clockFace.setSize(width, height);

    }

    public void kill(Context context) {
        Log.d(TAG, "kill");

        if(calendarFetcher != null) {
            calendarFetcher.kill();
            calendarFetcher = null;
        }

        if(clockState != null) {
            clockState.deleteObserver(this);
            clockState = null;
        }

        if(clockFace != null) {
            clockFace.kill();
            clockFace = null;
        }
    }


    private long drawCounter = 0;

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        Log.v(TAG, "onSizeChanged: " + w + ", " + h);
        this.width = w;
        this.height = h;
        if(clockFace != null)
            clockFace.setSize(width, height);
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_UP)
            performClick();

        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        PhoneActivity.watchfaceClick(this);

        return true;
    }
}

