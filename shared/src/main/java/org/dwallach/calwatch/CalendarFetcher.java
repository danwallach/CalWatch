/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.format.DateUtils;
import android.util.Log;

import org.dwallach.calwatch.TimeWrapper;
import org.dwallach.calwatch.WireEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CalendarFetcher {
    private static final String TAG = "CalendarFetcher";
    /**
     * queries the calendar database with proper Android APIs (ugly stuff)
     */
    public static List<WireEvent> loadContent(Uri uri, Context context) {
        // local copy; don't overwrite the class variable until we're done!
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

        // The URI builder stuff here is just enough different that this code isn't going to
        // be obviously portable between phone and watch. Long term, we obviously want to
        // deal with this.
//        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        Uri.Builder builder = uri.buildUpon();
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
                        return Long.compare(lhs.displayColor, rhs.displayColor);

                    if (lhs.endTime != rhs.endTime)
                        return Long.compare(lhs.endTime, rhs.endTime);

                    return Long.compare(rhs.startTime, lhs.startTime);
                }
            });
        }

        return cr;
    }
}
