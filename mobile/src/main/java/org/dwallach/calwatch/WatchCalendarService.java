package org.dwallach.calwatch;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class WatchCalendarService extends Service {
    public WatchCalendarService() {
        Log.v("WatchCalendarService", "starting calendar fetcher");
        CalendarFetcher cf = new CalendarFetcher(this); // automatically allocates a thread and runs
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
