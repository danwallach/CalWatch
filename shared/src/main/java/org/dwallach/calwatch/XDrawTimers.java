/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;

public class XDrawTimers {
    private static final String TAG = "XDrawTimers";

    public static long stopwatchStartTime;
    public static long stopwatchPriorTime;
    public static boolean stopwatchIsRunning;
    public static boolean stopwatchIsReset = true;
    public static long stopwatchUpdateTimestamp = 0;

    public static long getStopwatchUpdateTimestamp() {
        return stopwatchUpdateTimestamp;
    }

    public static void setStopwatchState(long startTime, long priorTime, boolean isRunning, boolean isReset, long updateTimestamp) {
        // ignore old / stale updates
        if(updateTimestamp > stopwatchUpdateTimestamp) {
            stopwatchStartTime = startTime;
            stopwatchPriorTime = priorTime;
            stopwatchIsRunning = isRunning;
            stopwatchIsReset = isReset;
            stopwatchUpdateTimestamp = updateTimestamp;
        }
    }


    public static long timerStartTime;
    public static long timerPauseDelta;
    public static long timerDuration;
    public static boolean timerIsRunning;
    public static boolean timerIsReset = true;
    public static long timerUpdateTimestamp = 0;

    public static long getTimerUpdateTimestamp() {
        return timerUpdateTimestamp;
    }

    public static void setTimerState(long startTime, long pauseDelta, long duration, boolean isRunning, boolean isReset, long updateTimestamp) {
        // ignore old / stale updates
        if(updateTimestamp > timerUpdateTimestamp) {
            timerStartTime = startTime;
            timerPauseDelta = pauseDelta;
            timerDuration = duration;
            timerIsRunning = isRunning;
            timerIsReset = isReset;
            timerUpdateTimestamp = updateTimestamp;
        }
    }
}

