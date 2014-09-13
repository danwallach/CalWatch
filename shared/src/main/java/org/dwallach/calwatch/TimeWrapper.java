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

    private static final long magicOffset = -12 * 60 * 60 * 1000; // twelve hours earlier, for debugging

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
