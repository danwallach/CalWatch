package org.dwallach.calwatch;

import java.util.TimeZone;

/**
 * We're asking for the time an awful lot for each different frame we draw, which
 * is actually having a real impact on performance. That's all centralized here
 * to fix the problem.
 */
public class TimeWrapper {
    private static TimeZone tz;
    private static int gmtOffset;
    private static long time;

    public static void update() {
        tz=TimeZone.getDefault();
        gmtOffset=tz.getRawOffset()+tz.getDSTSavings();
        time=System.currentTimeMillis()+gmtOffset;
    }
    
    public static TimeZone getTz() { return tz; }

    public static int getGmtOffset() { return gmtOffset; }

    public static long getTime() { return time; }

    static {
        // do this once at startup because why not?
        update();
    }
}
