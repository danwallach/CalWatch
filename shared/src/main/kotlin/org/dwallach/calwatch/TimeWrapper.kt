/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.os.SystemClock
import android.text.format.DateUtils
import org.jetbrains.anko.*

import java.util.TimeZone

/**
 * We're asking for the time an awful lot for each different frame we draw, which
 * is actually having a real impact on performance. That's all centralized here
 * to fix the problem.
 */
object TimeWrapper: AnkoLogger {
    var gmtOffset: Int = 0
        private set
    var gmtTime: Long = 0
        private set

    //   private static final long magicOffset = -40 * 60 * 60 * 1000 // 12 hours earlier, for debugging
    //   private static final long magicOffset = 25 * 60 * 1000       // 25 minutes later, for debugging
    private val magicOffset: Long = 0                      // for production use

    fun update() {
        gmtTime = System.currentTimeMillis() + magicOffset
        val tz = TimeZone.getDefault()
        gmtOffset = tz.getOffset(gmtTime) // includes DST correction
    }

    val localTime: Long
        get() = gmtTime + gmtOffset

    // 1000 * 60 * 60 = 3600000 -- the number of msec in an hour
    // if it's currently 12:32pm, this value returned will be 12:00pm
    val localFloorHour: Long
        get() = (Math.floor(localTime / 3600000.0) * 3600000.0).toLong()

    private var localMonthDayCache: String = ""
    private var localDayOfWeekCache: String = ""
    private var monthDayCacheTime: Long = 0

    private fun updateMonthDayCache() {
        // assumption: the day and month and such only change when we hit a new hour, otherwise we can reuse an old result
        val newCacheTime = localFloorHour
        if (newCacheTime != monthDayCacheTime || localMonthDayCache == "") {
            localMonthDayCache = DateUtils.formatDateTime(null, gmtTime, DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_DATE)
            localDayOfWeekCache = DateUtils.formatDateTime(null, gmtTime, DateUtils.FORMAT_SHOW_WEEKDAY)
            monthDayCacheTime = newCacheTime
        }
    }

    /**
     * Fetches something along the lines of "May 5", but in the current locale
     */
    fun localMonthDay(): String {
        updateMonthDayCache()
        return localMonthDayCache
    }

    /**
     * Fetches something along the lines of "Monday", but in the current locale
     */
    fun localDayOfWeek(): String {
        updateMonthDayCache()
        return localDayOfWeekCache

        /* -- old version, based on standard Java utils
        String format = "cccc"
        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault())
        return sdf.format(new Date())
        */
    }

    private var frameStartTime: Long = 0
    private var lastFPSTime: Long = 0
    private var samples = 0
    private var minRuntime: Long = 0
    private var maxRuntime: Long = 0
    private var avgRuntimeAccumulator: Long = 0
    private var skippedFrames = 0
    private var zombieFrames = 0

    /**
     * for performance monitoring: start the counters over again from scratch
     */
    fun frameReset() {
        samples = 0
        minRuntime = 0
        maxRuntime = 0
        avgRuntimeAccumulator = 0
        lastFPSTime = 0
        skippedFrames = 0
        zombieFrames = 0
    }

    /**
     * for performance monitoring: report the FPS counters and reset them immediately
     */
    fun frameReport() {
        frameReport(SystemClock.elapsedRealtimeNanos())
    }

    /**
     * Internal version, avoids multiple calls to get the system clock
     * @param currentTime
     */
    private fun frameReport(currentTime: Long) {
        val elapsedTime = currentTime - lastFPSTime // ns since last time we printed something
        if (samples > 0 && elapsedTime > 0) {
            val fps = samples * 1000000000f / elapsedTime  // * 10^9 so we're not just computing frames per nanosecond
            info { "FPS: %.3f, samples: $samples".format(fps) }

            info { "Min/Avg/Max frame render speed (ms): %.3f / %.3f / %.3f".format(
                minRuntime / 1000000f, + avgRuntimeAccumulator / samples / 1000000f, + maxRuntime / 1000000f ) }

            // this waketime percentage is really a lower bound; it's not counting work in the render thread
            // thread that's outside of the ClockFace rendering methods, and it's also not counting
            // work that happens on other threads
            info { "Waketime: %.3f %%".format(100f * avgRuntimeAccumulator / elapsedTime) }

            if (skippedFrames != 0)
                info { "Skipped frames: $skippedFrames" }
            if (zombieFrames != 0)
                info { "Zombie frames: $zombieFrames" }

            lastFPSTime = 0
        }
        frameReset()
    }

    /**
     * for performance monitoring: call this at the beginning of every screen refresh
     */
    fun frameStart() {
        frameStartTime = SystemClock.elapsedRealtimeNanos()
    }

    /**
     * for performance monitoring: call this at the end of every screen refresh
     */
    fun frameEnd() {
        val frameEndTime = SystemClock.elapsedRealtimeNanos()

        // first sample around, we're not remembering anything, just the time it ended; this gets on smooth footing for subsequent samples
        if (lastFPSTime == 0L) {
            lastFPSTime = frameEndTime
            return
        }

        val elapsedTime = frameEndTime - lastFPSTime // ns since last time we printed something
        val runtime = frameEndTime - frameStartTime  // ns since frameStart() called

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
