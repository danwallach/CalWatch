/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.util.Log

import java.io.IOException
import java.util.Observable

object ClockState : Observable() {
    private const val TAG = "ClockState"

    const val FACE_TOOL = 0
    const val FACE_NUMBERS = 1
    const val FACE_LITE = 2

    var faceMode: Int = Constants.DefaultWatchFace
    var showSeconds: Boolean = Constants.DefaultShowSeconds
    var showDayDate: Boolean = Constants.DefaultShowDayDate

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
    var wireInitialized = false
        private set
    var calendarPermission = false


    /**
     * Helper function to determine if we need subsecond refresh intervals.
     */
    fun subSecondRefreshNeeded(face: ClockFace) =
        // if the second-hand is supposed to be rendered and we're not in ambient mode
        showSeconds && !face.getAmbientMode()

    /**
     * Load the eventlist. This is meant to consume the output of the calendarFetcher,
     * which is in GMT time, *not* local time. Note that this method will *not* notify
     * any observers of the ClockState that the state has changed. This is because we
     * expect setWireEventList() to be called from other threads. Instead, pingObservers()
     * should be called externally, and only from the main UI thread. This is exactly what
     * CalendarFetcher does.
     */
    fun setWireEventList(eventList: List<WireEvent>) {
        Log.v(TAG, "fresh calendar event list, " + eventList.size + " entries")
        val (visibleEventList, maxLevel) = clipToVisible(eventList)
        Log.v(TAG, "--> " + visibleEventList + " visible events")
        this.eventList = eventList
        this.visibleEventList = visibleEventList
        this.maxLevel = maxLevel
    }

    private var lastClipTime: Long = 0

    private fun recomputeVisibleEvents() {
        if(!calendarPermission) return; // nothing we can do!

        // This is going to be called on every screen refresh, so it needs to be fast in the common case.
        // We're going to measure the time and try to figure out whether we've ticked onto
        // a new hour, which would mean that it's time to redo the visibility calculation.

        // Note that we're looking at "local" time here, so this means that any event that causes
        // us to change timezones will cause a difference in the hour and will also trigger the
        // recomputation.

        val localClipTime = TimeWrapper.localFloorHour

        if (lastClipTime == localClipTime)
            return

        // If we get here, that means we hit the top of a new hour. We're experimentally leaving
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
            it.copy(startTime = if(it.startTime < clipStartMillis) clipStartMillis else it.startTime,
                    endTime = if(it.endTime > clipEndMillis) clipEndMillis else it.endTime)
        }.filter {
            // keep only events that are onscreen
            it.endTime > clipStartMillis && it.startTime < clipEndMillis
            // get rid of events that go the full twelve-hour range
                    && !(it.endTime == clipEndMillis && it.startTime == clipStartMillis)
            // and get rid of events that start and end at precisely the same time
                    && it.endTime > it.startTime
        }.map {
            // apply GMT offset, and then wrap with EventWrapper, where the layout will happen
            EventWrapper(it.copy(startTime = it.startTime + gmtOffset, endTime = it.endTime + gmtOffset))
        }

        // now, we run off and do screen layout
        var lMaxLevel: Int

        if (clippedEvents.isNotEmpty()) {
            // first, try the fancy constraint solver
            if (EventLayoutUniform.go(clippedEvents)) {
                // yeah, we succeeded
                lMaxLevel = EventLayoutUniform.MAXLEVEL
            } else {
                // something blew up with the Simplex solver, fall back to the cheesy, greedy algorithm
                Log.v(TAG, "falling back to older greedy method")
                lMaxLevel = EventLayout.go(clippedEvents)
            }

            EventLayout.sanityTest(clippedEvents, lMaxLevel, "After new event layout")
            Log.v(TAG, "maxLevel for visible events: " + lMaxLevel)
            Log.v(TAG, "number of visible events: " + clippedEvents.size)

            return Pair(clippedEvents, lMaxLevel)
        } else {
            Log.v(TAG, "no events visible!")
            return Pair(emptyList(), 0)
        }
    }

    /**
     * This returns a list of *visible* events on the watchface, cropped to size, and adjusted to
     * the *local* timezone.
     */
    fun getVisibleEventList(): List<EventWrapper> {
        // should be fast, since mostly it will detect that nothing has changed
        recomputeVisibleEvents()
        return visibleEventList
    }

    /**
     * Marshalls into a protobuf for transmission elsewhere. (Well, it used to be
     * a protobuf, in ancient days, when we had to ship the entire calendar from
     * phone to watch, but now it's just a simple string.)
     */
    fun getProtobuf(): ByteArray {
        val wireUpdate = WireUpdate(faceMode, showSeconds, showDayDate)
        return wireUpdate.toByteArray()
    }
    /**
     * Load the ClockState with a protobuf containing a complete update
     * @param eventBytes a marshalled protobuf of type WireUpdate
     */
    fun setProtobuf(eventBytes: ByteArray) {
        Log.i(TAG, "setting protobuf: %d bytes".format(eventBytes.size))
        val wireUpdate: WireUpdate

        try {
            wireUpdate = WireUpdate.parseFrom(eventBytes)
            wireInitialized = true
        } catch (ioe: IOException) {
            Log.e(TAG, "parse failure on protobuf", ioe)
            return
        } catch (e: Exception) {
            if (eventBytes.size == 0)
                Log.e(TAG, "zero-length message received!")
            else
                Log.e(TAG, "some other weird failure on protobuf: nbytes(" + eventBytes.size + ")", e)
            return
        }

        faceMode = wireUpdate.faceMode
        showSeconds = wireUpdate.showSecondHand
        showDayDate = wireUpdate.showDayDate

        pingObservers()

        Log.v(TAG, "event update complete")
    }

    fun pingObservers() {
        // this incantation will make observers elsewhere aware that there's new content
        setChanged()
        notifyObservers()
        clearChanged()
    }

    private fun debugDump() {
        Log.v(TAG, "All events in the DB:")
        eventList.forEach {
            Log.v(TAG, "--> displayColor(" + Integer.toHexString(it.displayColor) + "), startTime(" + it.startTime + "), endTime(" + it.endTime + ")")
        }

        Log.v(TAG, "Visible:")
        visibleEventList.forEach {
            Log.v(TAG, "--> displayColor(" + Integer.toHexString(it.wireEvent.displayColor) + "), minLevel(" + it.minLevel + "), maxLevel(" + it.maxLevel + "), startTime(" + it.wireEvent.startTime + "), endTime(" + it.wireEvent.endTime + ")")
        }
    }
}


