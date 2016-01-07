/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.util.Log

import java.io.IOException
import java.util.ArrayList
import java.util.Observable

class ClockState private constructor() : Observable() {
    var faceMode: Int = Constants.DefaultWatchFace
    var showSeconds: Boolean = Constants.DefaultShowSeconds
    var showDayDate: Boolean = Constants.DefaultShowDayDate

    private var eventList: List<WireEvent>? = null
    private var visibleEventList: List<EventWrapper>? = null

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
     * Load the eventlist. This is meant to consume the output of the calendarFetcher,
     * which is in GMT time, *not* local time.
     */
    fun setWireEventList(eventList: List<WireEvent>) {
        Log.v(TAG, "fresh calendar event list, " + eventList.size + " entries")
        val (visibleEventList, maxLevel) = clipToVisible(eventList)
        this.eventList = eventList
        this.visibleEventList = visibleEventList
        this.maxLevel = maxLevel
//        pingObservers()
    }

    private var lastClipTime: Long = 0

    private fun recomputeVisibleEvents() {
        // This is going to be called on every screen refresh, so it needs to be fast in the common case.
        // The current solution is to measure the time and try to figure out whether we've ticked onto
        // a new hour, which would mean that it's time to redo the visibility calculation.

        val localClipTime = TimeWrapper.localFloorHour

        if (lastClipTime == localClipTime)
            return

        // If we get here, that means we hit the top of a new hour. We're experimentally leaving
        // the old data alone while we fire off a request to reload the calendar. This might take
        // a whole second or two, but at least it's not happening on the main UI thread.
        // TODO verify if this is a good strategy

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
            it.endTime >= clipStartMillis && it.startTime <= clipEndMillis &&
            // get rid of events that go the full twelve-hour range
                    !(it.endTime == clipEndMillis && it.startTime == clipStartMillis)
        }.map {
            // apply GMT offset, and then wrap with EventWrapper, where the layout will happen
            EventWrapper(it.copy(startTime = it.startTime + gmtOffset, endTime = it.endTime + gmtOffset))
        }

        // now, we run off and do screen layout
        var tmpMaxLevel: Int

        if (clippedEvents.isNotEmpty()) {
            // first, try the fancy constraint solver
            if (EventLayoutUniform.go(clippedEvents)) {
                // yeah, we succeeded
                tmpMaxLevel = EventLayoutUniform.MAXLEVEL
            } else {
                // something blew up with the Simplex solver, fall back to the cheesy, greedy algorithm
                Log.v(TAG, "falling back to older greedy method")
                tmpMaxLevel = EventLayout.go(clippedEvents)
            }

            EventLayout.sanityTest(clippedEvents, tmpMaxLevel, "After new event layout")
            Log.v(TAG, "maxLevel for new events: " + tmpMaxLevel)
            Log.v(TAG, "number of new events: " + clippedEvents.size)

            return Pair(clippedEvents, tmpMaxLevel)
        } else {
            Log.v(TAG, "no events visible!")
            return Pair(emptyList(), 0)
        }
    }

    /**
     * This returns a list of *visible* events on the watchface, cropped to size, and adjusted to
     * the *local* timezone.
     */
    fun getVisibleEventList(): List<EventWrapper>? {
        // should be fast, since mostly it will detect that nothing has changed
        recomputeVisibleEvents()
        return visibleEventList
    }

    var protobuf: ByteArray
        /**
         * Marshalls into a protobuf for transmission elsewhere. (Well, it used to be
         * a protobuf, in ancient days, when we had to ship the entire calendar from
         * phone to watch, but now it's just a simple string.)
         */
        get() {
            val wireUpdate = WireUpdate(faceMode, showSeconds, showDayDate)
            return wireUpdate.toByteArray()
        }
        /**
         * Load the ClockState with a protobuf containing a complete update
         * @param eventBytes a marshalled protobuf of type WireUpdate
         */
        set(eventBytes) {
            val wireUpdate: WireUpdate

            try {
                wireUpdate = WireUpdate.parseFrom(eventBytes)
                wireInitialized = true
            } catch (ioe: IOException) {
                Log.e(TAG, "parse failure on protobuf: nbytes(" + eventBytes.size + ")", ioe)
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
        if(eventList != null)
            for (e in eventList!!) {
                Log.v(TAG, "--> displayColor(" + Integer.toHexString(e.displayColor) + "), startTime(" + e.startTime + "), endTime(" + e.endTime + ")")
            }

        Log.v(TAG, "Visible:")
        if(visibleEventList != null)
            for (e in visibleEventList!!) {
                Log.v(TAG, "--> displayColor(" + Integer.toHexString(e.wireEvent.displayColor) + "), minLevel(" + e.minLevel + "), maxLevel(" + e.maxLevel + "), startTime(" + e.wireEvent.startTime + "), endTime(" + e.wireEvent.endTime + ")")
            }
    }

    companion object {
        private val TAG = "ClockState"

        const val FACE_TOOL = 0
        const val FACE_NUMBERS = 1
        const val FACE_LITE = 2

        private var singleton: ClockState? = null

        // instead, they'll go through this
        fun getState(): ClockState {
            val tmp = singleton

            if(tmp == null) {
                // total hack to avoid all the null-inference checks in Kotlin
                val newbie = ClockState()
                singleton = newbie
                return newbie
            } else {
                return tmp
            }
        }
    }
}


