package org.dwallach.calwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class WatchReceiver extends BroadcastReceiver {
    private final static String TAG = "WatchReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // This is how we wake up at boot time. All we're going to do from here is kick
        // the service into gear.

        Log.v(TAG, "boot-time: starting the calendar service");

        WatchCalendarService watchCalendarService = WatchCalendarService.getSingletonService();

        if (watchCalendarService == null) {
            Intent serviceIntent = new Intent(context, WatchCalendarService.class);
            context.startService(serviceIntent);
        }
    }
}
