/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.Observable;
import java.util.Observer;

public class WatchCalendarService extends Service implements Observer {
    private static final String TAG = "WatchCalendarService";

    private static WatchCalendarService singletonService;
    private WearSender wearSender;
    private ClockState clockState;

    private ClockState getClockState() {
        // more on the design of this particular contraption in the comments in PhoneActivity
        if(clockState == null) {
            clockState = ClockState.getSingleton();
            clockState.addObserver(this);
        }
        return clockState;
    }


    public WatchCalendarService() {
        super();
    }


    public static WatchCalendarService getSingletonService() {
        return singletonService;
    }

    // this is called when there's something new from the calendar DB
    public void sendAllToWatch() {
        if (wearSender == null) {
            Log.e(TAG, "no wear sender?!");
            return;
        }

        // and now, send on to the wear device
        wearSender.sendAllToWatch();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.v(TAG, "service starting!");

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    private void initInternal() {
        BatteryWrapper.init(this);
        getClockState();
        wearSender = new WearSender(this);

        PreferencesHelper.loadPreferences(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "service created!");

        initInternal();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "service destroyed!");

        clockState.deleteObserver(this);
        clockState = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.e(TAG, "onBind: we should support this");
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void update(Observable observable, Object data) {
        // somebody updated something in the clock state (new events, new display options, etc.)
        Log.v(TAG, "internal clock state changed: time to send all to the watch");
        sendAllToWatch();
    }

    public static void kickStart(Context ctx) {
        // start the calendar service, if it's not already running
        WatchCalendarService watchCalendarService = WatchCalendarService.getSingletonService();

        if(watchCalendarService == null) {
            Log.v(TAG, "launching watch calendar service");
            Intent serviceIntent = new Intent(ctx, WatchCalendarService.class);
            ctx.startService(serviceIntent);
        }

    }
}
