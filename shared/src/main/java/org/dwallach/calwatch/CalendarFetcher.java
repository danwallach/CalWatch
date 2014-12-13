/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.content.ContentUris;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.ConditionVariable;
import android.os.PowerManager;
import android.provider.CalendarContract;
import android.text.format.DateUtils;
import android.util.Log;

import java.util.Collections;
import java.util.Comparator;
import java.util.Observable;

/**
 * Created by dwallach on 8/13/14.
 *
 * Support for extracting calendar data from the platform's Calendar service.
 */
public class CalendarFetcher extends AsyncTask<Void, Void, Integer> {
    private final static String TAG = "CalendarFetcher";
    private Context ctx;
    private INewEvents eventNotifier;

    public CalendarFetcher(Context ctx, INewEvents eventNotifier) {
        Log.v(TAG, "Calendar fetcher created");
        this.ctx = ctx;
        this.eventNotifier = eventNotifier;
    }

    private PowerManager.WakeLock wakeLock;

    @Override
    protected Integer doInBackground(Void... voids) {
        PowerManager powerManager = (PowerManager) ctx.getSystemService(ctx.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "CalWatchFaceWakeLock");
        wakeLock.acquire();

        return loadMaybe(ctx);

//        long begin = System.currentTimeMillis();
//        Uri.Builder builder =
//                WearableCalendarContract.Instances.CONTENT_URI.buildUpon();
//        ContentUris.appendId(builder, begin);
//        ContentUris.appendId(builder, begin + DateUtils.DAY_IN_MILLIS);
//        final Cursor cursor = getContentResolver().query(builder.build(),
//                null, null, null, null);
//        int numMeetings = cursor.getCount();
    }

        @Override
        protected void onPostExecute(Integer result) {
            releaseWakeLock();
            if(eventNotifier != null)
                eventNotifier.newEvents(result);
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

    // just to confuse things, we're simultaneously an *observer* of the underlying
    // calendar database *and* we're *observable* by other classes
    private volatile CalendarResults calendarResults = null;

    private long lastQueryStartTime = 0;
    private long lastGMTOffset = 0;

    public int loadMaybe(Context ctx) {
        Log.v(TAG, "loadMaybe");

        boolean newContentAvailable = false;

        TimeWrapper.update();
        long currentGMTOffset = TimeWrapper.getGmtOffset();
        long queryStartMillis = TimeWrapper.getLocalFloorHour() - currentGMTOffset;
        long time = TimeWrapper.getGMTTime();
        long queryEndMillis = queryStartMillis + 86400000; // 24 hours later

        if(queryStartMillis != lastQueryStartTime || lastGMTOffset != currentGMTOffset) { // we've rolled to a new hour, or the timezone changed, so it's time to reload!
            newContentAvailable = true;
            lastQueryStartTime = queryStartMillis;
            lastGMTOffset = currentGMTOffset;
        }

        CalendarResults tmp = null;

        // this boolean could have been made true in a number of ways: either because we hit a new hour (as above) or because our cursor on the calendar detected a change
        if(newContentAvailable) {
            try {
                tmp = loadContent(ctx, time, queryStartMillis, queryEndMillis);
            } catch (Throwable throwable) {
                // most likely, we're not fully initialized yet, so just march along
                Log.e(TAG, "failure loading calendar; probably not ready yet, don't panic");
            }

            if (tmp != null) {
                return tmp.getWrappedEvents().size();
            }
        }

        return 0; // nothing found
    }

    /**
     * queries the calendar database with proper Android APIs (ugly stuff)
     */
    private CalendarResults loadContent(Context ctx, long time, long queryStartMillis, long queryEndMillis) {
        // local copy; don't overwrite the class variable until we're done!
        CalendarResults cr = new CalendarResults();

        // first, get the list of calendars
        Log.v(TAG, "starting to load content");
        if(ctx == null) {
            Log.e(TAG, "No query context!");
            return null;
        }

        try {
            Log.v(TAG, "Query times... Now: " + DateUtils.formatDateTime(ctx, time, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME) +
                    ", QueryStart: " + DateUtils.formatDateTime(ctx, queryStartMillis, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE) +
                    ", QueryEnd: " + DateUtils.formatDateTime(ctx, queryEndMillis, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE));
        } catch (Throwable t) {
            // sometimes the date formatting blows up... who knew? best to just ignore and move on
        }

        // And now, the event instances

        final String[] instancesProjection = new String[] {
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.VISIBLE,
        };

        // now, get the list of events
        Cursor iCursor = CalendarContract.Instances.query(ctx.getContentResolver(), instancesProjection, queryStartMillis, queryEndMillis);

        // if it's null, which shouldn't ever happen, then we at least won't gratuitously fail here
        if(iCursor != null) {
            if (iCursor.moveToFirst()) {
                do {
                    CalendarResults.Instance instance = new CalendarResults.Instance();
                    int i = 0;

                    instance.startTime = iCursor.getLong(i++);
                    instance.endTime = iCursor.getLong(i++);
                    instance.eventID = iCursor.getLong(i++);
                    instance.displayColor = iCursor.getInt(i++);
                    instance.allDay = (iCursor.getInt(i++) != 0);
                    instance.visible = (iCursor.getInt(i++) != 0);

                    if (instance.visible && !instance.allDay)
                        cr.instances.add(instance);

//                Log.v(TAG, "visible instances found: " + instance.toString());
                } while (iCursor.moveToNext());
            }

            // lifecycle cleanliness: important to close down when we're done
            iCursor.close();
        }


        if(cr.instances.size() > 1) {
            // Primary sort: color, so events from the same calendar will become consecutive wedges

            // Secondary sort: endTime, with objects ending earlier appearing first in the sort.
            //   (goal: first fill in the outer ring of the display with smaller wedges; the big
            //    ones will end late in the day, and will thus end up on the inside of the watchface)

            // Third-priority sort: startTime, with objects starting later (smaller) appearing first in the sort.


            Collections.sort(cr.instances, new Comparator<CalendarResults.Instance>() {
                @Override
                public int compare(CalendarResults.Instance lhs, CalendarResults.Instance rhs) {
                    if(lhs.displayColor != rhs.displayColor)
                        return Long.compare(lhs.displayColor, rhs.displayColor);

                    if (lhs.endTime != rhs.endTime)
                        return Long.compare(lhs.endTime, rhs.endTime);

                    return Long.compare(rhs.startTime, lhs.startTime);
                }
            });
        }

        Log.v(TAG, "database found instances(" + cr.instances.size() + ")");

        return cr;
    }
}
