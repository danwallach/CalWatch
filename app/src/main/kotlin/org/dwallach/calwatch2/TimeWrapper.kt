/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2022 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import android.os.SystemClock
import android.text.format.DateUtils
import android.util.Log
import java.util.TimeZone
import kotlin.math.floor

private val TAG = "TimeWrapper"

/**
 * We're asking for the time an awful lot for each different frame we draw, which
 * is actually having a real impact on performance. That's all centralized here
 * to fix the problem.
 */
object TimeWrapper {

    /**
     * Offset from GMT time to local time (including daylight savings correction, if necessary), in milliseconds.
     */
    var gmtOffset: Int = 0
        private set

    /** Current time, GMT, in milliseconds. */
    var gmtTime: Long = 0
        private set

    //   private const val magicOffset: Long = -40 * 60 * 60 * 1000 // 12 hours earlier, for debugging
    //   private const val magicOffset: Long = 25 * 60 * 1000       // 25 minutes later, for debugging
    private const val magicOffset: Long = 0 // for production use

    fun update() {
        gmtTime = System.currentTimeMillis() + magicOffset

        // TODO: do we want to migrate from java.util.TimeZone to android.icu.util.TimeZone?
        // - might work better in weird cases
        // - only supported in Android 7.0 and higher, which would be an issue for Wear 1.0.
        //   (assuming any of them are still around?)
        val tz = TimeZone.getDefault()

        gmtOffset = tz.getOffset(gmtTime) // includes DST correction
    }

    /**
     * Helper function: returns the local time (including daylight savings correction, if necessary) in milliseconds.
     */
    val localTime: Long
        get() = gmtTime + gmtOffset

    /** If it's currently 12:32pm, this value returned will be 12:00pm. */
    val localFloorHour: Long
        get() = (floor(localTime / 3600000.0) * 3600000.0).toLong()

    private var localMonthDayCache: String = ""
    private var localDayOfWeekCache: String = ""
    private var monthDayCacheTime: Long = 0

    private fun updateMonthDayCache() {
        // Assumption: the day and month and such only change when we hit a new hour,
        // otherwise we can reuse an old result.

        // In an early beta of CalWatch, I ran the profiler and discovered that calling
        // DateUtils.formatDateTime(), which I was doing as part of my onDraw() method,
        // was a huge time sink, generated a bunch of garbage, etc. Needless to say,
        // this was an obvious performance optimization.

        // The nice part about using Android's DateUtils here is that we get a fully
        // localized result.

        val newCacheTime = localFloorHour
        if (newCacheTime != monthDayCacheTime || localMonthDayCache == "") {
            localMonthDayCache =
                DateUtils.formatDateTime(null, gmtTime, DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_DATE)
            localDayOfWeekCache = DateUtils.formatDateTime(null, gmtTime, DateUtils.FORMAT_SHOW_WEEKDAY)
            monthDayCacheTime = newCacheTime
        }
    }

    /** Fetches something along the lines of "May 5", but in the current locale. */
    fun localMonthDay(): String {
        updateMonthDayCache()
        return localMonthDayCache
    }

    /** Fetches something along the lines of "Monday", but in the current locale. */
    fun localDayOfWeek(): String {
        updateMonthDayCache()
        return localDayOfWeekCache
    }

    private var frameStartTime: Long = 0
    private var lastFPSTime: Long = 0
    private var samples = 0
    private var minRuntime: Long = 0
    private var maxRuntime: Long = 0
    private var avgRuntimeAccumulator: Long = 0

    /** For performance monitoring: start the counters over again from scratch. */
    fun frameReset() {
        samples = 0
        minRuntime = 0
        maxRuntime = 0
        avgRuntimeAccumulator = 0
        lastFPSTime = 0
    }

    /** For performance monitoring: report the FPS counters and reset them immediately. */
    fun frameReport() = frameReport(SystemClock.elapsedRealtimeNanos())

    /**
     * Internal version, avoids multiple calls to get the system clock.
     * @param currentTime
     */
    private fun frameReport(currentTime: Long) {
        //
        // Note that the externally visible TimeWrapper APIs report the current time at a resolution
        // of milliseconds, while our internal framerate measurement and reporting are using the
        // nanosecond-accurate system clock counter. Thus, for the frame* functions, you'll see
        // different correction factors.
        //

        val elapsedTime = currentTime - lastFPSTime // ns since last time we printed something
        if (samples > 0 && elapsedTime > 0) {
            val fps = samples * 1000000000f / elapsedTime // * 10^9 so we're not just computing frames per nanosecond
            Log.i(TAG, "FPS: %.3f, samples: $samples".format(fps))

            Log.i(TAG,
                "Min/Avg/Max frame render speed (ms): %.3f / %.3f / %.3f".format(
                    minRuntime / 1000000f, +avgRuntimeAccumulator / samples / 1000000f, +maxRuntime / 1000000f
                )
            )

            // this waketime percentage is really a lower bound; it's not counting work in the render thread
            // thread that's outside of the ClockFace rendering methods, and it's also not counting
            // work that happens on other threads
            Log.i(TAG, "Waketime: %.3f %%".format(100f * avgRuntimeAccumulator / elapsedTime))

            lastFPSTime = 0
        }
        frameReset()
    }

    /** For performance monitoring: call this at the beginning of every screen refresh. */
    fun frameStart() {
        frameStartTime = SystemClock.elapsedRealtimeNanos()
    }

    /** For performance monitoring: call this at the end of every screen refresh. */
    fun frameEnd() {
        val frameEndTime = SystemClock.elapsedRealtimeNanos()

        // first sample around, we're not remembering anything, just the time it ended; this gets on smooth footing for subsequent samples
        if (lastFPSTime == 0L) {
            lastFPSTime = frameEndTime
            return
        }

        val elapsedTime = frameEndTime - lastFPSTime // ns since last time we printed something
        val runtime = frameEndTime - frameStartTime // ns since frameStart() called

        if (samples == 0) {
            avgRuntimeAccumulator = runtime
            minRuntime = runtime
            maxRuntime = runtime
        } else {
            if (runtime < minRuntime)
                minRuntime = runtime
            if (runtime > maxRuntime)
                maxRuntime = runtime
            avgRuntimeAccumulator += runtime
        }

        samples++

        // if at least one minute has elapsed, then it's time to print all the things
        if (elapsedTime > 60000000000L) {
            // 60 * 10^9 nanoseconds: one minute
            frameReport(frameEndTime)
        }
    }

    init {
        // do this once at startup because why not?
        update()
    }
}

/** Helper function: convert from seconds to our internal time units (milliseconds). */
val Double.seconds: Long get() = (this * 1000.0).toLong()

/** Helper function: convert from minutes to our internal time units (milliseconds). */

val Double.minutes: Long get() = (this * 60000.0).toLong()

/** Helper function: convert from hours to our internal time units (milliseconds). */
val Double.hours: Long get() = (this * 3600000.0).toLong()

/** Helper function: convert from seconds to our internal time units (milliseconds). */
val Long.seconds: Long get() = (this * 1000L)

/** Helper function: convert from minutes to our internal time units (milliseconds). */
val Long.minutes: Long get() = (this * 60000L)

/** Helper function: convert from hours to our internal time units (milliseconds). */
val Long.hours: Long get() = (this * 3600000L)

/** Helper function: convert from seconds to our internal time units (milliseconds). */
val Int.seconds: Long get() = (this * 1000L)

/** Helper function: convert from minutes to our internal time units (milliseconds). */
val Int.minutes: Long get() = (this * 60000L)

/** Helper function: convert from hours to our internal time units (milliseconds). */
val Int.hours: Long get() = (this * 3600000L)
