package org.dwallach.calwatch;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class WatchReceiver extends BroadcastReceiver {
    private final static String TAG = "WatchReceiver";
    private boolean firstTime = true;

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

        // Maybe this belongs better in WatchCalendarService rather than here. Keeping it here
        // makes some sense, since we want to set this up from the very beginning. The only
        // case where this won't happen is when the app is being started right after install,
        // so the boot-time action will never happen. That suggests that we only really need
        // to fire an intent from the service to the receiver when the receiver starts up.
        // That will, in turn, fire back as above.
        // TODO add a broadcast intent to WatchCalendarService to kickstart WatchReceiver if it's dead
        if(firstTime) {
            Log.v(TAG, "boot-time: set up alarm intent");
            // code pilfered in part from: http://www.vogella.com/tutorials/AndroidServices/article.html

            // goal: the onReceive method will get kicked once every 15 minutes or so, which will then
            // in turn kick the calendar service. We really, really don't want the service to stay dead
            // for very long, if ever.
            Intent alarmIntent = new Intent(context, WatchReceiver.class);
            PendingIntent pending = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);


            AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), AlarmManager.INTERVAL_FIFTEEN_MINUTES, pending);

            firstTime = false;
        }
    }
}
