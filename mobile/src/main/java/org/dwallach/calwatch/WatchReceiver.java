package org.dwallach.calwatch;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/*
 * This sad and pathetic class serves one function: to continually kick the WatchCalendarService
 * back to life if it's somehow managed to die
 */
public class WatchReceiver extends BroadcastReceiver {
    private final static String TAG = "WatchReceiver";

    private static volatile boolean firstTime = true;
    private static volatile long lastTime = -1;

    @Override
    public void onReceive(Context context, Intent intent) {
        long currentTime = System.currentTimeMillis();
        lastTime = currentTime;

        // This is how we wake up at boot time. All we're going to do from here is kick
        // the service into gear.

        Log.v(TAG, "boot-time: starting the calendar service");

        WatchCalendarService.kickStart(context); // launch it, if it's not already running

        if(firstTime) {
            Log.v(TAG, "boot-time: set up alarm intent");
            // code pilfered in part from: http://www.vogella.com/tutorials/AndroidServices/article.html

            // goal: the onReceive method will get kicked once every 15 minutes or so, which will then
            // in turn kick the calendar service. We really, really don't want the service to stay dead
            // for very long, if ever.
            Intent alarmIntent = new Intent(context, WatchReceiver.class);
            PendingIntent pending = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);


            AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, currentTime, AlarmManager.INTERVAL_FIFTEEN_MINUTES, pending);

            firstTime = false;
        }
    }

    /**
     * to be used by the PhoneActivity to try to get the receiver running
     */
    public static void kickStart(Context ctx) {
        long currentTime = System.currentTimeMillis();

        if(lastTime == -1 || currentTime - lastTime > 20 * 60 * 1000) {
            Log.v(TAG, "Kickstart launching intent");
            // we're supposed to receive this intent roughly once every 15 minutes; if it's been
            // longer than that, or if we've just never run before, then we need to bring out some
            // bigger guns

            Intent intent = new Intent();
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            intent.setAction("org.dwallach.calwatch.WAKE");
            ctx.sendBroadcast(intent);
        }
    }
}
