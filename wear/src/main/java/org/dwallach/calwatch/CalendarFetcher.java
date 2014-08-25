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

public class CalendarFetcher implements Runnable {
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
    private volatile CalendarResults calendarResults = null;

    private long lastQueryStartTime = 0;

    public void run() {
        Log.v("CalendarFetcher", "CalendarFetcher starting");
        running = true;

        for(;;) {
            if(newContentAvailable) {
                this.calendarResults = loadContent(); // lots of work happens here... which is why we're on a separate thread

                newContentAvailable = false;
            }

            conditionWait.close();
            if(conditionWait.block(2000)) { // wait for two seconds, wake up, and see what's up
                // we were signalled
                Log.v("CalendarFetcher", "CalendarFetcher: Wakeup signal received");
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



    /**
     * queries the calendar database with proper Android APIs (ugly stuff)
     */
    private CalendarResults loadContent() {
        // local copy; don't overwrite the class variable until we're done!
        CalendarResults cr = new CalendarResults();

        // first, get the list of calendars
        Log.v("CalendarFetcher", "CalendarFetcher starting to load content");

        //
        // TODO: snarf content from a service running on the phone
        //

        Log.v("CalendarFetcher", "no database loaded, because we suck");

        return cr;
    }
}

