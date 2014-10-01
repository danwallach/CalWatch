package org.dwallach.calwatch;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * This sad and pathetic class serves one function: to continually kick the WatchCalendarService
 * back to life if it's somehow managed to die
 */
public class WakeupReceiver extends BroadcastReceiver {
    private final static String TAG = "WakeupReceiver";

    private static volatile boolean firstTime = true;
    private static volatile long lastTime = -1;

    @Override
    public void onReceive(Context context, Intent intent) {
        long currentTime = System.currentTimeMillis();
        lastTime = currentTime;

        Log.v(TAG, "received intent: " + intent.toString());

        // This is how we wake up at boot time. All we're going to do from here is kick
        // the service into gear.



        if(firstTime) {
            Log.v(TAG, "boot-time: set up alarm intent");
            onReceiveFancy(context, intent);

            firstTime = false;
        } else {
            Log.v(TAG, "attempting to kickstart the watch calendar service");
            WatchCalendarService.kickStart(context); // launch it, if it's not already running
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

    // code that does this in a much fancier way
    // https://android.googlesource.com/platform/packages/providers/CalendarProvider/+/master/src/com/android/providers/calendar/CalendarReceiver.java

//    static final String SCHEDULE = "com.android.providers.calendar.SCHEDULE_ALARM";
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private PowerManager.WakeLock mWakeLock;

    private void onReceiveFancy(Context xcontext, Intent intent) {
        final Context context = xcontext; // foolishness to make the inner class work

        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CalendarReceiver_Provider");
            mWakeLock.setReferenceCounted(true);
        }
        mWakeLock.acquire();
//        final String action = intent.getAction();
//        final ContentResolver cr = context.getContentResolver();
        final PendingResult result = goAsync();
        executor.submit(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "setting up alarm service");
                // code pilfered in part from: http://www.vogella.com/tutorials/AndroidServices/article.html

                // goal: the onReceive method will get kicked once every 15 minutes or so, which will then
                // in turn kick the calendar service. We really, really don't want the service to stay dead
                // for very long, if ever.
                Intent alarmIntent = new Intent(context, WakeupReceiver.class);
                PendingIntent pending = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);


                AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, 0, AlarmManager.INTERVAL_FIFTEEN_MINUTES, pending);

                WatchCalendarService.kickStart(context);

                //
                //  original code from Google example
                //
//                if (action.equals(SCHEDULE)) {
//                    cr.update(CalendarAlarmManager.SCHEDULE_ALARM_URI, null /* values */,
//                            null /* where */, null /* selectionArgs */);
//                } else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
//                    removeScheduledAlarms(cr);
//                }
                result.finish();
                mWakeLock.release();
            }
        });
    }

}
