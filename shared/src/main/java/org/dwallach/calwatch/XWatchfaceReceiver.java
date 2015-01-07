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

/**
 * Simple code to watch for updates (via broadcast intents) to the shared x.stopwatch and
 * x.timer state, transmitted by any compliant stopwatch or coundown timer apps.
 *
 * Note that this just dumps out its state into a series of public static variables,
 * which you should feel free to read (and re-read) in your watchface's onDraw() handler.
 *
 * If you want to get some sort of notification, then you should hack that behavior into the onReceive
 * method somewhere.
 */
public class XWatchfaceReceiver extends BroadcastReceiver {
    private final static String TAG = "XWatchfaceReceiver";

    private static final String prefStopwatchRunning = "running";
    private static final String prefStopwatchReset = "reset";
    private static final String prefStopwatchStartTime = "start";
    private static final String prefStopwatchBaseTime = "base";
    private static final String prefStopwatchUpdateTimestamp = "updateTimestamp";

    private static final String prefTimerRunning = "running";
    private static final String prefTimerReset = "reset";
    private static final String prefTimerStartTime = "start";
    private static final String prefTimerPauseElapsed = "elapsed";
    private static final String prefTimerDuration = "duration";
    private static final String prefTimerUpdateTimestamp = "updateTimestamp";

    private static final String stopwatchUpdateIntent = "org.dwallach.x.stopwatch.update";
    private static final String stopwatchQueryIntent = "org.dwallach.x.stopwatch.query";

    private static final String timerUpdateIntent = "org.dwallach.x.timer.update";
    private static final String timerQueryIntent = "org.dwallach.x.timer.query";

    /**
     * the time (GMT) when the user clicked "start" on the stopwatch
     */
    public static long stopwatchStart;

    /**
     * base time to add in to account for when the stopwatch wasn't running
     */
    public static long stopwatchBase;

    /**
     * is the stopwatch running?
     */
    public static boolean stopwatchIsRunning;

    /**
     * Is the stopwatch reset? If so, you can assume it's not running and the value is zero.
     * You can furthermore not bother rendering it at all.
     */
    public static boolean stopwatchIsReset;

    /**
     * The last time at which we received an update to the stopwatch status. If the user is,
     * for some reason, running multiple stopwatches, all of which are generating the same
     * intent messages, the latest one that the user touched will have the most recent timestamp
     * on its messages, and that's the one you'll be displaying.
     */
    public static long stopwatchUpdateTimestamp;

    /**
     * The time (GMT) when the user began running the countdown timer. If the users pauses
     * the timer and later restarts it, this value will change. If the current time is
     * equal to timerStart, then the countdown timer should read the duration value.
     * If the current time is greater than or equal to timerStart + duration, then the
     * timer has completed. (The timer should then reset itself.)
     */
    public static long timerStart;

    /**
     * If the timer is paused, this is how much time has elapsed; subtract from
     * duration to know how much time is left. Ignore this if the timer is running.
     */
    public static long timerPauseElapsed;

    /**
     * The total amount of time for the timer.
     */
    public static long timerDuration;


    /**
     * Is the timer running?
     */
    public static boolean timerIsRunning;

    /**
     * Is the timer reset? If so, you can assume its value is equal to the duration.
     * You can also assume it's not running and you may choose not to display anything at all.
     */
    public static boolean timerIsReset;

    /**
     * The last time at which we received an update to the timer status. If the user is,
     * for some reason, running multiple timers, all of which are generating the same
     * intent messages, the latest one that the user touched will have the most recent timestamp
     * on its messages, and that's the one you'll be displaying.
     */
    public static long timerUpdateTimestamp;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.v(TAG, "got intent: " + action);

        if(action.equals(stopwatchUpdateIntent)) {
            Bundle extras = intent.getExtras();
            stopwatchStart = extras.getLong(prefStopwatchStartTime);
            stopwatchBase = extras.getLong(prefStopwatchBaseTime);
            stopwatchIsRunning = extras.getBoolean(prefStopwatchRunning);
            stopwatchIsReset = extras.getBoolean(prefStopwatchReset);
            stopwatchUpdateTimestamp = extras.getLong(prefStopwatchUpdateTimestamp);

            Log.v(TAG, "stopwatch update: start(" + stopwatchStart +
                    "), base(" + stopwatchBase +
                    "), isRunning(" + stopwatchIsRunning +
                    "), isReset(" + stopwatchIsReset +
                    "), updateTimestamp(" + stopwatchUpdateTimestamp +
                    ")");

        } else if (action.equals(timerUpdateIntent)) {
            Bundle extras = intent.getExtras();
            timerStart = extras.getLong(prefTimerStartTime);
            timerPauseElapsed = extras.getLong(prefTimerPauseElapsed);
            timerDuration = extras.getLong(prefTimerDuration);
            timerIsRunning = extras.getBoolean(prefTimerRunning);
            timerIsReset = extras.getBoolean(prefTimerReset);
            timerUpdateTimestamp = extras.getLong(prefTimerUpdateTimestamp);

            Log.v(TAG, "timer udpate:: start(" + timerStart +
                    "), elapsed(" + timerPauseElapsed +
                    "), duration(" + timerDuration +
                    "), isRunning(" + timerIsRunning +
                    "), isReset(" + timerIsReset +
                    "), updateTimestamp(" + timerUpdateTimestamp +
                    ")");
        }
    }

    /**
     * Call this and it will ask external stopwatches and timers to report back with their
     * state. If we already *have* state locally and nothing has changed remotely, then this
     * be a no-op.
     *
     * If you've called this and the state in the relevant variables here is still empty,
     * then it's possible there are no stopwatches running at all. If one of them shows up,
     * then the messages will be received here and written into the relevant public static fields.
     *
     * Once you're up and running, you don't need to call this again, as you'll presumably be registered to
     * receive these messages automatically.
     *
     * Best called in places like onCreate(). Best not called in onDraw().
     *
     * @param context
     */
    public static void pingExternalStopwatches(Context context) {
        if(stopwatchUpdateTimestamp == 0) {
            Log.v(TAG, "sending broadcast query for external stopwatches");
            context.sendBroadcast(new Intent(stopwatchQueryIntent));
        }
        if(timerUpdateTimestamp == 0) {
            Log.v(TAG, "sending broadcast query for external timers");
            context.sendBroadcast(new Intent(timerQueryIntent));
        }
    }
}
