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

public class WatchCalendarService extends Service {
    private static final String TAG = "WatchCalendarService";

    private static WatchCalendarService singletonService;
    private CalendarFetcher calendarFetcher;

    public WatchCalendarService() {
        super();
        Log.v(TAG, "constructor");
    }


    public static WatchCalendarService getSingletonService() {
        if(singletonService == null)
            Log.e(TAG, "error: no singleton service instantiated yet");
        return singletonService;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.v(TAG, "service starting!");

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "service created!");

        BatteryWrapper.init(this);

        Log.v(TAG, "starting calendar fetcher");
        if (singletonService != null) {
            Log.v(TAG, "whoa, multiple services!");
            if (calendarFetcher != null)
                calendarFetcher.haltUpdates();
        }

        singletonService = this;

        calendarFetcher = new CalendarFetcher(this);

        calendarFetcher.addObserver(new Observer() {
            @Override
            public void update(Observable observable, Object data) {
                Log.v(TAG, "New calendar state!");

                // the following line is important: this is where we bridge the output of the
                // calendar fetcher (running as a separate thread inside the Service)
                // into the ClockState central repo (shared by many things).

                ClockState.getSingleton().setEventList(calendarFetcher.getContent().getWrappedEvents());
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "service destroyed!");

        calendarFetcher.haltUpdates();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.e(TAG, "onBind: we should support this");
        throw new UnsupportedOperationException("Not yet implemented");
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
