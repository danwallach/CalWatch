/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.app.Notification;
import android.app.NotificationManager;
import android.util.Log;

/**
 * Created by dwallach on 11/17/14.
 */
public class WearNotificationHelper {
    private static final String TAG = "WearNotificationHelper";

    private static long firstTimeSeen, lastNotificationTime;
    private static boolean seenMessage = false;
    private static boolean initialized = false;

    private static int iconID;
    private static String title;
    private static String body;

    /**
     * Set up the notification helper. If !active, then all other methods become no-ops.
     * The idea is that the other methods are wired into various parts of the code, and
     * it's helpful to have a central place to disable them.
     * @param active true if you want these methods to work; false if you want them no-ops
     * @param iconID identifier for the icon to be shown in the notification
     * @param title title for the nag notifications that will be shown to the user
     * @param body body text for the nag notifications
     */
    public static void init(boolean active, int iconID, String title, String body) {
        Log.v(TAG, "init: active=" + active);
        if(!active)
            seenMessage = true; // disables further notifications
        initialized = true;

        // grumble: normally I'd just say this.foo = foo, but for statics you have to
        // type this extra yuck, but I can't quite bring myself to change styles and
        // use extraneous _'s or m's or other prefixes on member variables.

        WearNotificationHelper.iconID = iconID;
        WearNotificationHelper.title = title;
        WearNotificationHelper.body = body;

        TimeWrapper.update();
        firstTimeSeen = TimeWrapper.getGMTTime();
    }

    public static void seenPhone(Context context) {
        if(!seenMessage) {
            // there's a chance that there's a notification up; we need to nuke it
            Log.v(TAG, "nuking any notifications, just in case");

            try {
                // Gets an instance of the NotificationManager service
                NotificationManager notifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                // Builds the notification and issues it.
                notifyManager.cancelAll();

            } catch (Throwable throwable) {
                Log.e(TAG, "failed to cancel notifications", throwable);
            } finally {
                seenMessage = true;
            }
        }
    }

    public static void maybeNotify(Context context) {
        // we'll only bug the user if we never got a message from the phone, and we'll
        // wait until we've been initialized before doing anything
        if(seenMessage || !initialized) return;

        // We're running inside the redraw loop, so we're not allowed to blow up, ever.
        // Also note, we only log once we've decided it's time to do a notification. Otherwise
        // we'd potentially dump to the log at 40Hz, and that's not acceptable.

        try {

            long currentTime = TimeWrapper.getGMTTime();
            // if ten seconds since we booted and and ten minutes since previous nag
            if (currentTime - firstTimeSeen > 10000 && currentTime - lastNotificationTime > 600000) {
                lastNotificationTime = currentTime;

                Log.v(TAG, "can't see the phone; nagging the user");
                notifyUser(context);
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "maybeNotify failed", throwable);
        }
    }

    private static final int notificationID = 001;

    private static void notifyUser(Context context) {
        // Google docs for this:
        // http://developer.android.com/training/notify-user/build-notification.html

        Notification.Builder builder =
                new Notification.Builder(context)
                        .setAutoCancel(true)    // nuke if the user touches it, but we'll bring it back later....
                        .setSmallIcon(iconID)
                        .setContentTitle(title)
                        .setContentText(body);
        Notification notification = builder.build();

        // Gets an instance of the NotificationManager service
        NotificationManager notifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        // Builds the notification and issues it.
        notifyManager.notify(notificationID, notification);
    }
}
