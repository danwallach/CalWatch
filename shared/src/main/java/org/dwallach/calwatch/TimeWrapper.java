package org.dwallach.calwatch;

import android.os.SystemClock;
import android.util.Log;

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

    public static void update() {
        tz=TimeZone.getDefault();
        gmtOffset=tz.getRawOffset()+tz.getDSTSavings();
        time=System.currentTimeMillis();
    }
    
    public static TimeZone getTz() { return tz; }

    public static int getGmtOffset() { return gmtOffset; }

    public static long getGMTTime() { return time; }
    public static long getLocalTime() { return time + gmtOffset; }

    private static long frameStartTime;

    /**
     * for performance monitoring: call this at the beginning of every screen refresh
     */
    public static void frameStart() {
        frameStartTime = SystemClock.elapsedRealtimeNanos();
    }

    /**
     * for performance monitoring: call this at the end of every screen refresh
     */
    private static long lastFPSTime = 0;
    private static int ticks = 0;

    public static void frameEnd() {
        ticks++;

        long frameEndTime = SystemClock.elapsedRealtimeNanos();

        // first, let's sort out the average frames per second
        if(lastFPSTime == 0)
            lastFPSTime = frameEndTime;

        long elapsedTime = frameEndTime - frameStartTime;

        // work in progress: compute statistics

        // if at least five minutes have elapsed, then it's time to print all the things
        if(frameEndTime - lastFPSTime > 300000000000L) { // 5 * 60 * 10^9
            float fps = (ticks * 1000000000f) / (frameEndTime - lastFPSTime); // 500 frame * 1000 ms/s / elapsed ms
            lastFPSTime = frameEndTime;
            ticks = 0;
            Log.v(TAG, "FPS: " + Float.toString(fps));
        }
    }

    static {
        // do this once at startup because why not?
        update();
    }
}
