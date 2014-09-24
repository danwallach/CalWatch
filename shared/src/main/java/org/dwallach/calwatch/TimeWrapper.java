package org.dwallach.calwatch;

import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * We're asking for the time an awful lot for each different frame we draw, which
 * is actually having a real impact on performance. That's all centralized here
 * to fix the problem.
 */
public class TimeWrapper {
    private static final String TAG = "TimeWrapper";
    private static TimeZone tz;
    private static int gmtOffset;
    private static long time;

//    private static final long magicOffset = -39 * 60 * 60 * 1000; // 12 hours earlier, for debugging
//    private static final long magicOffset = 25 * 60 * 1000;       // 25 minutes later, for debugging
    private static final long magicOffset = 0;                      // for production use

    public static void update() {
        tz=TimeZone.getDefault();
        gmtOffset=tz.getRawOffset()+tz.getDSTSavings();
        time=System.currentTimeMillis() + magicOffset;
    }
    
    public static TimeZone getTz() { return tz; }

    public static int getGmtOffset() { return gmtOffset; }

    public static long getGMTTime() { return time; }
    public static long getLocalTime() { return time + gmtOffset; }

    public static long getLocalFloorHour() {
        // 1000 * 60 * 60 = 3600000 -- the number of msec in an hour
        // if it's currently 12:32pm, this value returned will be 12:00pm
        return (long) (Math.floor(getLocalTime() / 3600000.0) * 3600000.0);
    }

    public static String formatGMTTime(long millis) {
        DateFormat df = DateFormat.getDateTimeInstance();
        df.setTimeZone(TimeZone.getDefault());

        return df.format(new Date(millis));
    }

    private static String localMonthDayCache, localDayOfWeekCache;
    private static long monthDayCacheTime = 0;

    private static void updateMonthDayCache() {
        // assumption: the day and month and such only change when we hit a new hour, otherwise we can reuse an old result
        long newCacheTime = getLocalFloorHour();
        if(newCacheTime != monthDayCacheTime || localMonthDayCache == null) {
            localMonthDayCache = DateUtils.formatDateTime(null, getGMTTime(), DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_DATE);
            localDayOfWeekCache = DateUtils.formatDateTime(null, getGMTTime(), DateUtils.FORMAT_SHOW_WEEKDAY);
            monthDayCacheTime = newCacheTime;
        }
    }

    /**
     * Fetches something along the lines of "May 5", but in the current locale
     */
    public static String localMonthDay() {
        updateMonthDayCache();
        return localMonthDayCache;
    }

    /**
     * Fetches something along the lines of "Monday", but in the current locale
     */
    public static String localDayOfWeek() {
        updateMonthDayCache();
        return localDayOfWeekCache;

        /* -- old version, based on standard Java utils
        String format = "cccc";
        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
        return sdf.format(new Date());
        */
    }

    private static long frameStartTime = 0;
    private static long lastFPSTime = 0;
    private static int samples = 0;
    private static long minRuntime = 0;
    private static long maxRuntime = 0;
    private static long avgRuntimeAccumulator = 0;

    /**
     * for performance monitoring: call this at the beginning of every screen refresh
     */
    public static void frameStart() {
        frameStartTime = SystemClock.elapsedRealtimeNanos();
    }

    /**
     * for performance monitoring: call this at the end of every screen refresh
     */
    public static void frameEnd() {
        long frameEndTime = SystemClock.elapsedRealtimeNanos();

        if(lastFPSTime == 0)
            lastFPSTime = frameEndTime;

        long elapsedTime = frameEndTime - lastFPSTime; // ns since last time we printed something
        long runtime = frameEndTime - frameStartTime;  // ns since frameStart() called

        if(samples == 0) {
           avgRuntimeAccumulator = minRuntime = maxRuntime = runtime;
        } else {
            if(runtime < minRuntime)
                minRuntime = runtime;
            if(runtime > maxRuntime)
                maxRuntime = runtime;
            avgRuntimeAccumulator += runtime;
        }

        samples++;


        // if at least five minutes have elapsed, then it's time to print all the things
        if(elapsedTime > 60000000000L) { // 60 * 10^9 nanoseconds: one minute
            float fps = (samples * 1000000000f) / elapsedTime;  // * 10^9 so we're not just computing frames per nanosecond
            Log.i(TAG, "FPS: " + Float.toString(fps));

            Log.i(TAG, "Min/Avg/Max frame render speed (ms): "
                    + minRuntime / 1000000f + " / "
                    + (avgRuntimeAccumulator/samples)/1000000f + " / "
                    + maxRuntime / 1000000f);

            // this waketime percentage is really a lower bound; it's not counting work in the render thread
            // thread that's outside of the ClockFace rendering methods, and it's also not counting
            // work that happens on other threads
            Log.i(TAG, "Waketime: " + (100f * (avgRuntimeAccumulator - runtime) / elapsedTime) + "%");

            lastFPSTime = frameEndTime;
            samples = 0;
            minRuntime = 0;
            maxRuntime = 0;
            avgRuntimeAccumulator = 0;
        }
    }

    static {
        // do this once at startup because why not?
        update();
    }
}
