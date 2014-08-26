package org.dwallach.calwatch;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.ConditionVariable;
import android.os.SystemClock;
import android.provider.CalendarContract;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;

import com.android.calendarcommon2.DateException;
import com.android.calendarcommon2.Duration;
import com.android.calendarcommon2.EventRecurrence;
import com.android.calendarcommon2.RecurrenceProcessor;
import com.android.calendarcommon2.RecurrenceSet;

import org.dwallach.calwatch.proto.WireEvent;

import java.util.LinkedList;
import java.util.List;
import java.util.Observable;

/**
 * Created by dwallach on 8/13/14.
 *
 * Support for extracting calendar data from the platform's Calendar service.
 * Decent documentation: http://www.grokkingandroid.com/androids-calendarcontract-provider/
 */
public class CalendarFetcher extends Observable implements Runnable {
    private Context ctx = null;

    public CalendarFetcher(Context ctx) {
        Log.v("CalendarFetcher", "starting fetcher thread");

        this.ctx = ctx;
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

    public void run() {
        Log.v("CalendarFetcher", "CalendarFetcher starting");
        running = true;

        //
        // This is the event loop of the background thread. Its job is to snarf calendar content
        // whenever it changes. It will make at most one set of queries every two seconds, but that
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
        for(;;) {
            // Log.v("CalendarFetcher", "CalendarFetcher ping");
            long queryStartMillis = (long) (Math.floor(SystemClock.currentThreadTimeMillis() / 3600000.0) * 360000.0); // if it's currently 12:32pm, this value will be 12:00pm
            if(queryStartMillis > lastQueryStartTime) { // we've rolled to a new hour, so it's time to reload!
                newContentAvailable = true;
                lastQueryStartTime = queryStartMillis;
            }

            // this boolean could have been made true in a number of ways: either because we hit a new hour (as above) or because our cursor on the calendar detected a change
            if(newContentAvailable) {
                this.calendarResults = loadContent(); // lots of work happens here... which is why we're on a separate thread
                setChanged();
                notifyObservers();
                clearChanged();


                newContentAvailable = false;
            }

            conditionWait.close();
            if(conditionWait.block(2000)) { // wait for two seconds, wake up, and see what's up
                // we were signalled
                Log.v("CalendarFetcher", "CalendarFetcher: Wakeup signal received");
            } // else {
                // the timeout happened
                // Log.v("CalendarFetcher", "CalendarFetcher: Wakeup timeout");
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
        Log.v("CalendarFetcher", "Calendar: halt update");
        if(conditionWait != null) {
            running = false;
            conditionWait.open();
        }
    }

    public void resumeUpdates() {
        // we might get here from the UI thread even though we haven't finished setting things up yet,
        // so the proper answer is to quietly do nothing and return
        Log.v("CalendarFetcher", "Calendar: resume update");
        if(conditionWait != null) {
            running = true;
            newContentAvailable = true;
            conditionWait.open();
        }
    }




    private RecurrenceProcessor rprocessor = null;
    /**
     * queries the calendar database with proper Android APIs (ugly stuff)
     */
    private CalendarResults loadContent() {
        if(rprocessor == null)
            rprocessor = new RecurrenceProcessor();

        // local copy; don't overwrite the class variable until we're done!
        CalendarResults cr = new CalendarResults();

        // first, get the list of calendars
        Log.v("CalendarFetcher", "CalendarFetcher starting to load content");
        final String[] calProjection =
                new String[]{
                        CalendarContract.Calendars._ID,
                        CalendarContract.Calendars.NAME,
                        CalendarContract.Calendars.ACCOUNT_NAME,
                        CalendarContract.Calendars.ACCOUNT_TYPE,
                        CalendarContract.Calendars.CALENDAR_COLOR,
                        CalendarContract.Calendars.CALENDAR_COLOR_KEY,
                        CalendarContract.Calendars.VISIBLE
                };
        Cursor calCursor = ctx.getContentResolver().
                query(CalendarContract.Calendars.CONTENT_URI,
                        calProjection,
                        CalendarContract.Calendars.VISIBLE + " = 1",  // solve our UI problems by copying visibility from the main calendar (?)
                        null,
                        CalendarContract.Calendars._ID + " ASC");


        int calendarsFound = 0;
        if (calCursor.moveToFirst()) {
            do {
                int i = 0;
                CalendarResults.Calendar cal = new CalendarResults.Calendar();

                cal.ID = calCursor.getInt(i++);
                cal.name = calCursor.getString(i++);
                cal.accountName = calCursor.getString(i++);
                cal.accountType = calCursor.getString(i++);
                cal.calendarColor = calCursor.getInt(i++);
                cal.calendarColorKey = calCursor.getString(i++);
                cal.visible = (calCursor.getInt(i++) != 0);

                // Log.v("CalendarFetcher", "Found calendar. ID(" + cal.ID + "), name(" + cal.name + "), color(" + Integer.toHexString(cal.calendarColor) + "), colorKey(" + cal.calendarColorKey + "), accountName(" + cal.accountName + "), visible(" + Boolean.toString(cal.visible)+ ")");

                cr.calendars.put(cal.ID, cal);
                calendarsFound++;
            }
            while (calCursor.moveToNext()) ;
        }
        Log.v("CalendarFetcher", "calendars found ("+ calendarsFound + ")");

        calCursor.close();

        final String[] colorsProjection =
                new String[] {
                        CalendarContract.Colors.COLOR,
                        CalendarContract.Colors.COLOR_KEY
                };

        Cursor colorCursor = ctx.getContentResolver().query(CalendarContract.Colors.CONTENT_URI, colorsProjection, null, null, null);

        if(colorCursor.moveToFirst()) {
            do {
                int i = 0;
                CalendarResults.Color color = new CalendarResults.Color();

                color.argb = colorCursor.getInt(i++);
                color.key = colorCursor.getString(i++);
                color.paint = PaintCan.getPaint(color.argb);

                // Log.v("CalendarFetcher", "Found color. ID(" + color.key + "), argb=(" + Integer.toHexString(color.argb) + ")");

                cr.colors.put(color.key, color);
            } while(colorCursor.moveToNext());
        }

        colorCursor.close();

        // now, get the list of events
        // fantastically helpful:
        // http://stackoverflow.com/questions/10133616/reading-all-of-todays-events-using-calendarcontract-android-4-0
        // http://www.grokkingandroid.com/androids-calendarcontract-provider/
        // http://www.techrepublic.com/blog/software-engineer/programming-with-the-android-40-calendar-api-the-good-the-bad-and-the-ugly/


        long time = System.currentTimeMillis();
        long queryStartMillis = (long) (Math.floor(time / 3600000.0) * 3600000.0); // if it's currently 12:32pm, this value will be 12:00pm
        long queryEndMillis = queryStartMillis + 43200000; // twelve hours later

        Log.v("CalendarFetcher", "Query times... Now: " + DateUtils.formatDateTime(ctx, time, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME) + ", QueryStart: " + DateUtils.formatDateTime(ctx, queryStartMillis, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE)  + ", QueryEnd: " + DateUtils.formatDateTime(ctx, queryEndMillis, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE));

        // select events that end after the start of our window AND start before the end of our window,
        // filtering out any all-day events and any events that aren't visible
        // Note: < rather than <= because we don't care about an event that starts at the beginning of the next hour
        String eventSelection =
                "((" + CalendarContract.Events.DTSTART + " < ?) " +
                // "AND ((" + CalendarContract.Events.DTEND + " > ?) OR (" + CalendarContract.Events.DTEND + " = 0))" +
                "AND (" + CalendarContract.Events.ALL_DAY + " = 0) " +
                "AND (" + CalendarContract.Events.VISIBLE + " = 1)" +
                 ")";

        // commented out the DTEND part because recurring events don't have it at all. This means the query is pulling in a lot of
        // unnecessary events, but we'll deal with it. Also, the OR clause at the end of the DTEND thing didn't work at all; probably
        // needs to say something about empty string rather than zero.

        String[] eventSelectionArgs = new String[] {
                Long.toString(queryEndMillis)  // , Long.toString(queryStartMillis), // during today, not all day
        };


        final String[] eventProjection = new String[] {
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.ACCOUNT_NAME,
                CalendarContract.Events.ACCOUNT_TYPE,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.EVENT_COLOR,
                CalendarContract.Events.EVENT_COLOR_KEY,
                CalendarContract.Events.DISPLAY_COLOR,
                CalendarContract.Events.VISIBLE,
                CalendarContract.Events.RDATE,
                CalendarContract.Events.RRULE,
                CalendarContract.Events.EXDATE,
                CalendarContract.Events.EXRULE,
                CalendarContract.Events.DURATION,
        };

        // now, get the list of events
        Cursor mCursor = ctx.getContentResolver().query(
                CalendarContract.Events.CONTENT_URI, eventProjection, eventSelection, eventSelectionArgs, CalendarContract.Events.DTSTART + " ASC");

        if (mCursor.moveToFirst()) {
            do {
                CalendarResults.Event e = new CalendarResults.Event();
                int i=0;

                e.title = mCursor.getString(i++);
                e.startTime = mCursor.getLong(i++);
                e.endTime = mCursor.getLong(i++);
                e.allDay = (mCursor.getInt(i++) != 0);
                e.accountName = mCursor.getString(i++);
                e.accountType = mCursor.getString(i++);
                e.calendarID = mCursor.getInt(i++);
                e.eventColor = mCursor.getInt(i++);
                e.eventColorKey = mCursor.getString(i++);
                e.displayColor = mCursor.getInt(i++);
                e.visible = (mCursor.getInt(i++) != 0);
                e.rDate = mCursor.getString(i++);
                e.rRule = mCursor.getString(i++);
                e.exDate = mCursor.getString(i++);
                e.exRule = mCursor.getString(i++);
                e.duration = mCursor.getString(i++);

                e.paint = PaintCan.getPaint(e.displayColor); // at least on my phone, this tells you everything you need to know
                e.minLevel = e.maxLevel = -1;  // these are filled in later on by the EventLayout engine

                if(e.rDate != null || e.rRule != null || e.exDate != null || e.exRule != null) {
                    // holy crap, it's a recurring event
                    try {
                        // important to make a copy and mutate that rather than mutating the original; ensures
                        // that if there are multiple start times in the window that they'll have distinct
                        // events in the results rather than all pointing to the same object, having contents
                        // corresponding to the final iteration through the below loop
                        CalendarResults.Event ce = new CalendarResults.Event(e);
                        RecurrenceSet rset = new RecurrenceSet(ce.rRule, ce.rDate, ce.exDate, ce.exDate);
                        Time startTime = new Time();
                        startTime.set(ce.startTime);
                        long startTimes[] = rprocessor.expand(startTime, rset, queryStartMillis, queryEndMillis);

                        for (long st : startTimes) {
                            ce.startTime = st;
                            Duration d = new Duration();
                            d.parse(e.duration);
                            ce.endTime = ce.startTime + d.getMillis();

                            addEvent(cr, ce, queryStartMillis, queryEndMillis);
                        }

                    } catch (DateException de) {
                        // whatever... just means that we won't be adding any recurring events
                        Log.v("CalendarFetcher", "DateException: " + de.toString());
                    } catch (EventRecurrence.InvalidFormatException ie) {
                        // this shouldn't really happen, but at least it doesn't happen often!
                        Log.v("CalendarFetcher", "InvalidFormatException: Title(" + e.title +
                                "), dtStart(" + e.startTime +
                                "), dtEnd(" + e.endTime +
                                "), rRule(" + e.rRule +
                                "), rDate(" + e.rDate +
                                "), exRule(" + e.exRule +
                                "), exDate(" + e.exDate +
                                "), duration(" + e.duration +
                                "), " + ie.toString());

                    }
                } else addEvent(cr, e, queryStartMillis, queryEndMillis);

            } while (mCursor.moveToNext());
        }

        mCursor.close();

        // DONE!: deal with recurring events (RDATE, RRULE)
        // DONE!: deal with "recurring exceptions" (EXDATE, EXRULE)
        // relevant code? https://android.googlesource.com/platform/frameworks/opt/calendar/+/ics-mr1/src/com/android/calendarcommon/RecurrenceProcessor.java
        // and https://android.googlesource.com/platform/frameworks/opt/calendar/+/ics-mr1/src/com/android/calendarcommon/RecurrenceSet.java
        // StackOverflow relevance:
        // http://stackoverflow.com/questions/23342334/android-calendarcontract-recurring-event-with-exception-dates
        // http://stackoverflow.com/questions/13537315/in-calendarcontracts-instances-determine-if-original-event-is-recurring

        // curious open-source scanning contraption:
        // http://www.programcreek.com/java-api-examples/index.php?api=android.provider.CalendarContract

        // sort out the overlapping calendar layers
        cr.maxLevel = EventLayout.go(cr.events);

        Log.v("CalendarFetcher", "database found events(" + cr.events.size() + ")");

        // EventLayout.debugDump(ctx, cr.events);
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

    private void addEvent(CalendarResults cr, CalendarResults.Event e, long queryStartMillis, long queryEndMillis) {
        // Log.v("CalendarFetcher", "Found visible event. Title(" + e.title + "), calID(" + e.calendarID + "), eventColor(" + Integer.toHexString(e.eventColor) + "), eventColorKey(" + e.eventColorKey + "), displayColor(" + Integer.toHexString(e.displayColor) + ")");
        // Log.v("CalendarFetcher", "--> Start: " + DateUtils.formatDateTime(ctx, e.startTime, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME) + ", End: " + DateUtils.formatDateTime(ctx, e.endTime, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE));
        if (e.startTime < queryEndMillis && e.endTime > queryStartMillis && e.visible && !e.allDay) {
            // Log.v("CalendarFetcher", "--> Match!");

            // if we get here, the event is visible; we just need to clip it to fit the twelve hour window
            if (e.startTime < queryStartMillis) e.startTime = queryStartMillis;
            if (e.endTime > queryEndMillis) e.endTime = queryEndMillis;

            cr.events.add(e);
        }
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

