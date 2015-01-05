/*
 * Copyright (C) 2014 Dan Wallach <dwallach@rice.edu>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dwallach.calwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class XWatchfaceReceiver extends BroadcastReceiver {
    private final static String TAG = "XWatchfaceReceiver";

    private static final String prefStopwatchRunning = "running";
    private static final String prefStopwatchReset = "reset";
    private static final String prefStopwatchStartTime = "startTime";
    private static final String prefStopwatchPriorTime = "priorTime";
    private static final String prefStopwatchUpdateTimestamp = "updateTimestamp";

    public static final String prefTimerRunning = "running";
    public static final String prefTimerReset = "reset";
    public static final String prefTimerStartTime = "startTime";
    public static final String prefTimerPauseDelta = "pauseDelta";
    public static final String prefTimerDuration = "duration";
    public static final String prefTimerUpdateTimestamp = "updateTimestamp";

    private static final String stopwatchUpdateIntent = "org.dwallach.x.stopwatch.update";
    public static final String stopwatchQueryIntent = "org.dwallach.x.stopwatch.query";

    private static final String timerUpdateIntent = "org.dwallach.x.timer.update";
    public static final String timerQueryIntent = "org.dwallach.x.timer.query";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, "got intent: " + intent.toString());

        String action = intent.getAction();

        if(action.equals(stopwatchUpdateIntent)) {
            Bundle extras = intent.getExtras();
            long startTime = extras.getLong(prefStopwatchStartTime);
            long priorTime = extras.getLong(prefStopwatchPriorTime);
            boolean isRunning = extras.getBoolean(prefStopwatchRunning);
            boolean isReset = extras.getBoolean(prefStopwatchReset);
            long updateTimestamp = extras.getLong(prefStopwatchUpdateTimestamp);

            Log.v(TAG, "stopwatch update: startTime(" + startTime +
                    "), priorTime(" + priorTime +
                    "), isRunning(" + isRunning +
                    "), isReset(" + isReset +
                    "), updateTimestamp(" + updateTimestamp +
                    ")");

            XDrawTimers.setStopwatchState(startTime, priorTime, isRunning, isReset, updateTimestamp);

        } else if (action.equals(timerUpdateIntent)) {
            Bundle extras = intent.getExtras();
            long startTime = extras.getLong(prefTimerStartTime);
            long pauseDelta = extras.getLong(prefTimerPauseDelta);
            long duration = extras.getLong(prefTimerDuration);
            boolean isRunning = extras.getBoolean(prefTimerRunning);
            boolean isReset = extras.getBoolean(prefTimerReset);
            long updateTimestamp = extras.getLong(prefTimerUpdateTimestamp);

            Log.v(TAG, "timer udpate:: startTime(" + startTime +
                    "), pauseDelta(" + pauseDelta +
                    "), duration(" + duration +
                    "), isRunning(" + isRunning +
                    "), isReset(" + isReset +
                    "), updateTimestamp(" + updateTimestamp +
                    ")");

            XDrawTimers.setTimerState(startTime, pauseDelta, duration, isRunning, isReset, updateTimestamp);
        }
    }

    /**
     * Call this and it will ask external stopwatches and timers to report back with their
     * state. If we already *have* state locally, then this will be a no-op, so don't worry
     * about calling it too often. Best called in places like onCreate(). Best not called in onDraw().
     *
     * @param context
     */
    public static void pingExternalStopwatches(Context context) {
        if(XDrawTimers.getStopwatchUpdateTimestamp() == 0) {
            Log.v(TAG, "sending broadcast query for external stopwatches");
            context.sendBroadcast(new Intent(stopwatchQueryIntent));
        }
        if(XDrawTimers.getTimerUpdateTimestamp() == 0) {
            Log.v(TAG, "sending broadcast query for external timers");
            context.sendBroadcast(new Intent(timerQueryIntent));
        }
    }
}
