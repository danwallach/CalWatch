package org.dwallach.calwatch;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.ConditionVariable;
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
public class CalendarFetcher extends Observable implements Runnable {
    private final static String TAG = "CalendarFetcher";

    private Context ctx = null;

    public CalendarFetcher() {
        Log.v(TAG, "starting fetcher thread");

        this.ctx = WatchCalendarService.getSingletonService();
        this.conditionWait = new ConditionVariable();
        new Thread(this).start();
    }

    private ConditionVariable conditionWait;  // used to wake up the running thread
    private volatile boolean running = false;
    private volatile boolean newContentAvailable = true;

    // just to confuse things, we're simultaneously an *observer* of the underlying
    // calendar database *and* we're *observable* by other classes
    private MyObserver observer = null, observer2 = null;
    private volatile CalendarResults calendarResults = null;

    private long lastQueryStartTime = 0;
    private long lastGMTOffset = 0;

    public void run() {
        Log.v(TAG, "run: starting");
        running = true;

        //
        // This is the event loop of the background thread. Its job is to snarf calendar content
        // whenever it changes. It will make at most one query every two seconds, but that
        // would only ever happen if the calendar were actually *changing* that fast. In the common
        // case, where the calendar stays put, the observers that are watching it (set in loadContent())
        // won't fire and this event loop will simply wake up every two seconds, notice that newContentAvailable
        // is false, and promptly go back to sleep. It's worthwhile to have this loop wake up that
        // often, however, so we can detect lifecycle events, like if the app is no longer onscreen
        // or is otherwise paused. At that point, it's good manners to deregister the observers.
        // (They'll be reinstalled when we wake up again.)
        //
        // The most anti-social that this app can get, in the case when it's no longer active, is
        // that this thread will wake up every two seconds, check the time, and go to sleep again.
        // In that case, it will still query the calendar once an hour. That doesn't seem like it's
        // a particularly big power draw, and it means that when we wake back up, we'll have something
        // reasonably current to display, despite waking things up properly to do another round of queries
        // against the calendar.
        //
        // TODO: this could probably be replaced with a Looper or an alarm or something, but this works for now
        //
        for(;;) {
            // Log.v(TAG, "ping");
            TimeWrapper.update();
            long currentGMTOffset = TimeWrapper.getGmtOffset();
            long queryStartMillis = TimeWrapper.getLocalFloorHour() - currentGMTOffset;

            if(queryStartMillis != lastQueryStartTime || lastGMTOffset != currentGMTOffset) { // we've rolled to a new hour, or the timezone changed, so it's time to reload!
                newContentAvailable = true;
                lastQueryStartTime = queryStartMillis;
                lastGMTOffset = currentGMTOffset;
            }

            // this boolean could have been made true in a number of ways: either because we hit a new hour (as above) or because our cursor on the calendar detected a change
            if(newContentAvailable) {
                this.calendarResults = loadContent(); // lots of work happens here... which is why we're on a separate thread

                // this incantation will make observers elsewhere aware that there's new content
                setChanged();
                notifyObservers();
                clearChanged();


                newContentAvailable = false;
            }

            conditionWait.close();
            if(conditionWait.block(2000)) { // wait for two seconds, wake up, and see what's up
                // we were signalled
                Log.v(TAG, "Wakeup signal received");
            } // else {
                // the timeout happened
                // Log.v(TAG, " Wakeup timeout");
            // }

            if(!running && observer != null) {
                ctx.getContentResolver().unregisterContentObserver(observer);
                ctx.getContentResolver().unregisterContentObserver(observer2);
                observer = null;
                observer2 = null;
            }
        }
    }

    /**
     * returns whatever we've managed to extract from the calendar system; fetching will happen
     * continuously in a background thread and this gives you the best snapshot currently available
     * in a read-only format. Note that early results from this function may well be null and will
     * evolve over time. The intent is that it's *cheap* to call getContent(), so do it and parse
     * the results every time there's a screen refresh. The more expensive loadContent(), which
     * copies everything from the real calendar, runs asynchronously on its own thread, starting
     * automatically when the CalendarResults class is created.
     */
    public CalendarResults getContent() {
        return calendarResults;
    }

    /**
     * app lifecycle management: when it's time to stop paying attention, because the app isn't being
     * displayed or whatever
     */
    public void haltUpdates() {
        // we might get here from the UI thread even though we haven't finished setting things up yet,
        // so the proper answer is to quietly do nothing and return
        Log.v(TAG, "Calendar: halt update");
        if(conditionWait != null) {
            running = false;
            conditionWait.open();
        }
    }

    public void resumeUpdates() {
        // we might get here from the UI thread even though we haven't finished setting things up yet,
        // so the proper answer is to quietly do nothing and return
        Log.v(TAG, "Calendar: resume update");
        if(conditionWait != null) {
            running = true;
            newContentAvailable = true;
            conditionWait.open();
        }
    }




    /**
     * queries the calendar database with proper Android APIs (ugly stuff)
     */
    private CalendarResults loadContent() {
        // local copy; don't overwrite the class variable until we're done!
        CalendarResults cr = new CalendarResults();

        // first, get the list of calendars
        Log.v(TAG, "starting to load content");
        if(ctx == null) {
            Log.e(TAG, "No query context!");
            return null;
        }

        TimeWrapper.update();
        long time = TimeWrapper.getGMTTime();
        long queryStartMillis = TimeWrapper.getLocalFloorHour() - TimeWrapper.getGmtOffset();
        long queryEndMillis = queryStartMillis + 86400000; // 24 hours later

        Log.v(TAG, "Query times... Now: " + DateUtils.formatDateTime(ctx, time, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME) +
                ", QueryStart: " + DateUtils.formatDateTime(ctx, queryStartMillis, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE)  +
                ", QueryEnd: " + DateUtils.formatDateTime(ctx, queryEndMillis, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE));

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

                if(instance.visible && !instance.allDay)
                    cr.instances.add(instance);

//                Log.v(TAG, "visible instances found: " + instance.toString());
            } while (iCursor.moveToNext());
        }

        if(cr.instances.size() > 1) {
            // Primary sort: color, so events from the same calendar will become consecutive wedges

            // Secondary sort: endTime, with objects ending earlier appearing first in the sort.
            //   (goal: first fill in the outer ring of the display with smaller wedges; the big
            //    ones will end late in the day, and will thus end up on the inside of the watchface)

            // Third-priority sort: startTime, with objects starting earlier appearing first in the sort.


            Collections.sort(cr.instances, new Comparator<CalendarResults.Instance>() {
                @Override
                public int compare(CalendarResults.Instance lhs, CalendarResults.Instance rhs) {
                    if(lhs.displayColor != rhs.displayColor)
                        return Long.compare(lhs.displayColor, rhs.displayColor);

                    if (lhs.endTime != rhs.endTime)
                        return Long.compare(lhs.endTime, rhs.endTime);

//                    if (lhs.startTime != rhs.startTime)
                    return Long.compare(lhs.startTime, lhs.endTime);
                }
            });
        }

        Log.v(TAG, "database found instances(" + cr.instances.size() + ")");

        /*
         * register an observer in case something changes
         */
        if(observer == null) {
            observer = new MyObserver();
            observer2 = new MyObserver();
            ctx.getContentResolver().registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer);
            ctx.getContentResolver().registerContentObserver(CalendarContract.Calendars.CONTENT_URI, true, observer2);
        }

        return cr;
    }

    /*
     * code stolen from: http://www.grokkingandroid.com/use-contentobserver-to-listen-to-changes/
     */
    class MyObserver extends ContentObserver {
        public MyObserver() {
            super(null);
        }


        @Override
        public void onChange(boolean selfChange) {

            this.onChange(selfChange, null);
        }

        @Override

        public void onChange(boolean selfChange, Uri uri) {
            // note that this could run on whatever thread, so we can't do anything more here
            // than notify the thread that cares and promptly go away.

            newContentAvailable = true;
            conditionWait.open();  // signal the thread to snarf new content
        }
    }
}
