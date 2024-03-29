/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2022 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch2

import android.util.Log

private val TAG = "ClockState"

/**
 * We're doing something of the model-view-controller thing here, where ClockState has the "model" --
 * everything necessary to render a clockface including what style it is, whether we're supposed
 * to show the seconds-hand or the day-date display, and the list of events. The "controller" part
 * is [CalWatchFaceService]. The "view" (i.e., all the actual graphics calls) lives in [ClockFace].
 *
 * The idea is that there is a ClockState singleton, and it doesn't know anything about Android
 * contexts or any of that stuff.
 */
object ClockState {
    const val FACE_TOOL = 0
    const val FACE_NUMBERS = 1
    const val FACE_LITE = 2

    var faceMode: Int = Constants.DEFAULT_WATCHFACE
    var showSeconds: Boolean = Constants.DEFAULT_SHOW_SECONDS
    var showDayDate: Boolean = Constants.DEFAULT_SHOW_DAY_DATE

    private var eventList: List<CalendarEvent> = emptyList()
    private var visibleEventList: List<EventWrapper> = emptyList()

    var maxLevel: Int = 0
        private set

    var calendarPermission = false

    /** Helper function to determine if we need subsecond refresh intervals. */
    fun subSecondRefreshNeeded(face: ClockFace?) =
    // if the second-hand is supposed to be rendered and we're not in ambient mode
        if (face == null) false else showSeconds && !face.ambientMode

    /**
     * Load the eventlist. This is meant to consume the output of [CalendarFetcher]
     * which is in GMT time, *not* local time.
     */
    fun setEventList(eventList: List<CalendarEvent>, layoutPair: Pair<List<EventWrapper>, Int>) {
        Log.v(TAG, "fresh calendar event list, ${eventList.size} entries")
        val (visibleEventList, maxLevel) = layoutPair
        Log.v(TAG, "--> $visibleEventList visible events")
        this.eventList = eventList
        this.visibleEventList = visibleEventList
        this.maxLevel = maxLevel
    }

    private var lastClipTime: Long = 0

    private fun recomputeVisibleEvents() {
        if (!calendarPermission) return // nothing we can do!

        // This is going to be called on every screen refresh, so it needs to be fast in the common case.
        // We're going to measure the time and try to figure out whether we've ticked onto
        // a new hour, which would mean that it's time to redo the visibility calculation.

        // Note that we're looking at "local" time here, so this means that any event that causes
        // us to change timezones will cause a difference in the hour and will also trigger the
        // recomputation.

        val localClipTime = TimeWrapper.localFloorHour

        if (lastClipTime == localClipTime) return

        // If we get here, that means we hit the top of a new hour. We're leaving
        // the old data alone while we fire off a request to reload the calendar. This might take
        // a whole second or two, but at least it's not happening on the main UI thread.

        lastClipTime = localClipTime
        CalendarFetcher.requestRescan()
    }

    /**
     * This returns a list of *visible* events on the watchface, cropped to size, and adjusted to
     * the *local* timezone.
     */
    fun getVisibleEventList(): List<EventWrapper> {
        recomputeVisibleEvents() // might start an async update, might not
        return visibleEventList // return the best current data we've got
    }

    private fun debugDump() {
        Log.v(TAG, "All events in the DB:")
        eventList.forEach {
            Log.v(TAG, "--> displayColor(%06x), startTime(${it.startTime}), endTime(${it.endTime})".format(it.displayColor))
        }

        Log.v(TAG, "Visible:")
        visibleEventList.forEach {
            Log.v(TAG,
                "--> displayColor(%06x), minLevel(${it.minLevel}), maxLevel(${it.maxLevel}), startTime(${it.calendarEvent.startTime}), endTime(${it.calendarEvent.endTime})"
                    .format(it.calendarEvent.displayColor)
            )
        }
    }
}
