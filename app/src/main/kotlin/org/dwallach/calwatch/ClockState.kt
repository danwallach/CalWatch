/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import org.jetbrains.anko.*

import java.util.Observable

/**
 * We're doing something of the model-view-controller thing here, where ClockState has the "model" --
 * everything necessary to render a clockface including what style it is, whether we're supposed
 * to show the seconds-hand or the day-date display, and the list of events. The "controller" part
 * is different on the phone and on the watch (MyViewAnim/PhoneActivity vs. CalWatchFaceService).
 * The "view" (i.e., all the actual graphics calls) lives in ClockFace.
 *
 * The idea is that there is a ClockState singleton, and it doesn't know anything about Android
 * contexts or any of that stuff.
 */
object ClockState : Observable(), AnkoLogger {
    const val FACE_TOOL = 0
    const val FACE_NUMBERS = 1
    const val FACE_LITE = 2

    var faceMode: Int = Constants.DEFAULT_WATCHFACE
    var showSeconds: Boolean = Constants.DEFAULT_SHOW_SECONDS
    var showDayDate: Boolean = Constants.DEFAULT_SHOW_DAY_DATE
    var showStepCounter: Boolean = Constants.DEFAULT_SHOW_STEP_COUNTER

    private var eventList: List<WireEvent> = emptyList()
    private var visibleEventList: List<EventWrapper> = emptyList()

    var maxLevel: Int = 0
        private set

    /**
     * Query whether or not a wire update has arrived yet. If the result is false,
     * *and* we're running on the watch, then we've only got default values. If we're
     * running on the phone, this data structure might be otherwise initialized, so
     * this getter might return false even when the data structure is loaded with events.
     * @return if a wire message has successfully initialized the clock state
     */
    var calendarPermission = false

    /**
     * Helper function to determine if we need subsecond refresh intervals.
     */
    fun subSecondRefreshNeeded(face: ClockFace?) =
        // if the second-hand is supposed to be rendered and we're not in ambient mode
        if (face == null) false else showSeconds && !face.ambientMode

    /**
     * Load the eventlist. This is meant to consume the output of the calendarFetcher,
     * which is in GMT time, *not* local time. Note that this method will *not* notify
     * any observers of the ClockState that the state has changed. This is because we
     * expect setWireEventList() to be called from other threads. Instead, pingObservers()
     * should be called externally, and only from the main UI thread. This is exactly what
     * CalendarFetcher does.
     */
    fun setWireEventList(eventList: List<WireEvent>) {
        verbose { "fresh calendar event list, " + eventList.size + " entries" }
        val (visibleEventList, maxLevel) = clipToVisible(eventList)
        verbose { "--> $visibleEventList visible events" }
        this.eventList = eventList
        this.visibleEventList = visibleEventList
        this.maxLevel = maxLevel
    }

    private var lastClipTime: Long = 0

    private fun recomputeVisibleEvents() {
        if(!calendarPermission) return // nothing we can do!

        // This is going to be called on every screen refresh, so it needs to be fast in the common case.
        // We're going to measure the time and try to figure out whether we've ticked onto
        // a new hour, which would mean that it's time to redo the visibility calculation.

        // Note that we're looking at "local" time here, so this means that any event that causes
        // us to change timezones will cause a difference in the hour and will also trigger the
        // recomputation.

        val localClipTime = TimeWrapper.localFloorHour

        if (lastClipTime == localClipTime)
            return

        // If we get here, that means we hit the top of a new hour. We're leaving
        // the old data alone while we fire off a request to reload the calendar. This might take
        // a whole second or two, but at least it's not happening on the main UI thread.

        lastClipTime = localClipTime
        CalendarFetcher.requestRescan()
    }

    /**
     * Given a list of events, return another list that corresponds to the set of
     * events visible in the next twelve hours, with events that would be off-screen
     * clipped to the 12-hour dial.
     */
    private fun clipToVisible(events: List<WireEvent>): Pair<List<EventWrapper>, Int> {
        val gmtOffset = TimeWrapper.gmtOffset

        val localClipTime = TimeWrapper.localFloorHour
        val clipStartMillis = localClipTime - gmtOffset // convert from localtime back to GMT time for looking at events
        val clipEndMillis = clipStartMillis + 43200000  // 12 hours later

        val clippedEvents = events.map {
            // chop the start/end of the event to fit onscreen
            it.copy(startTime = if(it.startTime < clipStartMillis) clipStartMillis else it.startTime,
                    endTime = if(it.endTime > clipEndMillis) clipEndMillis else it.endTime)
        }.filter {
            // require events to be onscreen
            it.endTime > clipStartMillis && it.startTime < clipEndMillis
            // require events to not fill the full screen
                    && !(it.endTime == clipEndMillis && it.startTime == clipStartMillis)
            // require events to have some non-zero thickness (clipping can sometimes yield events that start and end at the same time)
                    && it.endTime > it.startTime
        }.map {
            // apply GMT offset, and then wrap with EventWrapper, where the layout will happen
            EventWrapper(it.copy(startTime = it.startTime + gmtOffset, endTime = it.endTime + gmtOffset))
        }

        // now, we run off and do screen layout
        val lMaxLevel: Int

        if (clippedEvents.isNotEmpty()) {
            // first, try the fancy constraint solver
            if (EventLayoutUniform.go(clippedEvents)) {
                // yeah, we succeeded
                lMaxLevel = EventLayoutUniform.MAXLEVEL
            } else {
                error("event layout failed!") // in years of testing, this failure apparently *never* happened
                return Pair(emptyList(), 0)
            }

            EventLayoutUniform.sanityTest(clippedEvents, lMaxLevel, "After new event layout")
            verbose { "maxLevel for visible events: $lMaxLevel" }
            verbose { "number of visible events: ${clippedEvents.size}" }

            return Pair(clippedEvents, lMaxLevel)
        } else {
            verbose("no events visible!")
            return Pair(emptyList(), 0)
        }
    }

    /**
     * This returns a list of *visible* events on the watchface, cropped to size, and adjusted to
     * the *local* timezone.
     */
    fun getVisibleEventList(): List<EventWrapper> {
        recomputeVisibleEvents() // might start an async update, might not
        return visibleEventList // return the best current data we've got
    }

    /**
     * Call this for all the ClockState observers to get updated.
     */
    fun pingObservers() {
        // this incantation will make observers elsewhere aware that there's new content
        setChanged()
        notifyObservers()
        clearChanged()
    }

    private fun debugDump() {
        verbose("All events in the DB:")
        eventList.forEach {
            verbose { "--> displayColor(%06x), startTime(${it.startTime}), endTime(${it.endTime})".format(it.displayColor) }
        }

        verbose("Visible:")
        visibleEventList.forEach {
            verbose { "--> displayColor(%06x), minLevel(${it.minLevel}), maxLevel(${it.maxLevel}), startTime(${it.wireEvent.startTime}), endTime(${it.wireEvent.endTime})"
                    .format(it.wireEvent.displayColor) }
        }
    }
}


