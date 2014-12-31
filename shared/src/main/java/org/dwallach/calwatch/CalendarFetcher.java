/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.CalendarContract;
import android.text.format.DateUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CalendarFetcher {
    private static final String TAG = "CalendarFetcher";

    private Uri contentUri;

    // yes, saving a Context is evil, but we need to keep it around for the loadContent
    // task, which runs asynchronously
    private Context context;

    public CalendarFetcher(Context context, Uri contentUri, String authority) {
        this.contentUri = contentUri;
        this.context = context;

        // hook into watching the calendar (code borrowed from Google's calendar wear app)
        Log.v(TAG, "setting up intent receiver");
        IntentFilter filter = new IntentFilter(Intent.ACTION_PROVIDER_CHANGED);
        filter.addDataScheme("content");
        filter.addDataAuthority(authority, null);
        context.registerReceiver(broadcastReceiver, filter);
        isReceiverRegistered = true;

        // kick off initial loading of calendar state
        loaderHandler.sendEmptyMessage(MSG_LOAD_CAL);
    }

    public void kill() {
        Log.v(TAG, "kill");

        if (isReceiverRegistered) {
            context.unregisterReceiver(broadcastReceiver);
            isReceiverRegistered = false;
        }

        cancelLoaderTask();
        loaderHandler.removeMessages(MSG_LOAD_CAL);
    }

    /**
     * queries the calendar database with proper Android APIs (ugly stuff)
     */
    public List<WireEvent> loadContent() {
        // local state which we'll eventually return
        List<WireEvent> cr = new ArrayList<WireEvent>();

        // first, get the list of calendars
        Log.v(TAG, "starting to load content");
        if (context == null) {
            Log.e(TAG, "No query context!");
            return null;
        }

        TimeWrapper.update();
        long time = TimeWrapper.getGMTTime();
        long queryStartMillis = TimeWrapper.getLocalFloorHour() - TimeWrapper.getGmtOffset();
        long queryEndMillis = queryStartMillis + 86400000; // 24 hours later

        try {
            Log.v(TAG, "Query times... Now: " + DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME) +
                    ", QueryStart: " + DateUtils.formatDateTime(context, queryStartMillis, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE) +
                    ", QueryEnd: " + DateUtils.formatDateTime(context, queryEndMillis, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE));
        } catch (Throwable t) {
            // sometimes the date formatting blows up... who knew? best to just ignore and move on
        }

        // And now, the event instances

        final String[] instancesProjection = new String[]{
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.VISIBLE,
        };

        // now, get the list of events
        long begin = System.currentTimeMillis();

        Uri.Builder builder = contentUri.buildUpon();
        ContentUris.appendId(builder, queryStartMillis);
        ContentUris.appendId(builder, queryEndMillis);
        final Cursor iCursor = context.getContentResolver().query(builder.build(),
                instancesProjection, null, null, null);


        // if it's null, which shouldn't ever happen, then we at least won't gratuitously fail here
        if (iCursor != null) {
            if (iCursor.moveToFirst()) {
                do {
                    int i = 0;

                    long startTime = iCursor.getLong(i++);
                    long endTime = iCursor.getLong(i++);
                    long eventID = iCursor.getLong(i++);
                    int displayColor = iCursor.getInt(i++);
                    boolean allDay = (iCursor.getInt(i++) != 0);
                    boolean visible = (iCursor.getInt(i++) != 0);

                    if (visible && !allDay)
                        cr.add(new WireEvent(startTime, endTime, displayColor));

                } while (iCursor.moveToNext());
                Log.v(TAG, "visible instances found: " + cr.size());
            }

            // lifecycle cleanliness: important to close down when we're done
            iCursor.close();
        }


        if (cr.size() > 1) {
            // Primary sort: color, so events from the same calendar will become consecutive wedges

            // Secondary sort: endTime, with objects ending earlier appearing first in the sort.
            //   (goal: first fill in the outer ring of the display with smaller wedges; the big
            //    ones will end late in the day, and will thus end up on the inside of the watchface)

            // Third-priority sort: startTime, with objects starting later (smaller) appearing first in the sort.


            Collections.sort(cr, new Comparator<WireEvent>() {
                @Override
                public int compare(WireEvent lhs, WireEvent rhs) {
                    if (lhs.displayColor != rhs.displayColor)
                        return lcompare(lhs.displayColor, rhs.displayColor);

                    if (lhs.endTime != rhs.endTime)
                        return lcompare(lhs.endTime, rhs.endTime);

                    return lcompare(rhs.startTime, lhs.startTime);
                }
            });
        }

        return cr;
    }

    // Arrgghh: java.util.Long.compare() isn't defined until API level 19 and we're trying
    // to hit API level 17, thus we need this.
    private static int lcompare(long a, long b) {
        if(a<b)
            return -1;
        if(a>b)
            return 1;
        return 0;
    }

    /**
     * Asynchronous task to load the calendar instances.
     */
    private class CalLoaderTask extends AsyncTask<Void, Void, List<WireEvent>> {
        private PowerManager.WakeLock wakeLock;

        @Override
        protected List<WireEvent> doInBackground(Void... voids) {
            if(context == null) {
                Log.e(TAG, "no saved context: can't do background loader");
                return null;
            }

            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "CalWatchWakeLock");
            wakeLock.acquire();

            Log.v(TAG, "wake lock acquired");

            return loadContent();
        }

        @Override
        protected void onPostExecute(List<WireEvent> results) {
            releaseWakeLock();

            Log.v(TAG, "wake lock released");

            try {
                ClockState.getSingleton().setWireEventList(results);
            } catch(Throwable t) {
                Log.e(TAG, "unexpected failure setting wire event list from calendar");
            }
        }

        @Override
        protected void onCancelled() {
            releaseWakeLock();
        }

        private void releaseWakeLock() {
            if (wakeLock != null) {
                wakeLock.release();
                wakeLock = null;
            }
        }
    }

    private static final int MSG_LOAD_CAL = 1;
    private AsyncTask<Void,Void,List<WireEvent>> loaderTask;

    // this will fire when it's time to (re)load the calendar, launching an asynchronous
    // task to do all the dirty work and eventually update ClockState
    final Handler loaderHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case MSG_LOAD_CAL:
                    cancelLoaderTask();
                    Log.v(TAG, "launching calendar loader task");

                    loaderTask = new CalLoaderTask();
                    loaderTask.execute();
                    break;
                default:
                    Log.e(TAG, "unexpected message: " + message.toString());
            }
        }
    };

    private boolean isReceiverRegistered;

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "receiver: got intent message.  action(" + intent.getAction() + "), data(" + intent.getData() + "), toString(" + intent.toString() + ")");
            if (Intent.ACTION_PROVIDER_CHANGED.equals(intent.getAction())) {

                // Google's reference code also checks that the Uri matches intent.getData(), but the URI we're getting back via intent.getData() is:
                // content://com.google.android.wearable.provider.calendar
                //
                // versus the URL we're looking for in the first place:
                // content://com.google.android.wearable.provider.calendar/instances/when

                // Solution? Screw it. Whatever we get, we don't care, we'll reload the calendar.

                Log.v(TAG, "receiver: time to load new calendar data");
                cancelLoaderTask();
                loaderHandler.sendEmptyMessage(MSG_LOAD_CAL);
            }
        }
    };

    private void cancelLoaderTask() {
        if (loaderTask != null) {
            loaderTask.cancel(true);
        }
    }

}
