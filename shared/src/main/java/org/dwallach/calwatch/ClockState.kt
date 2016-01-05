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

    private var eventList: List<EventWrapper>? = null
    private var visibleEventList: List<EventWrapper>? = null

    var maxLevel = 0
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
    fun setWireEventList(wireEventList: List<WireEvent>) {
        val results = ArrayList<EventWrapper>()

        for (wireEvent in wireEventList)
            results.add(EventWrapper(wireEvent))

        this.eventList = results
        this.visibleEventList = null
        pingObservers()
        Log.v(TAG, "new calendar event list, " + results.size + " entries")
    }

    private var lastClipTime: Long = 0

    private fun computeVisibleEvents() {
        //        if(eventList == null) {
        //            Log.v(TAG, "no events to compute visibility over, going with empty list for now");
        //        }

        // This is going to be called on every screen refresh, so it needs to be fast in the common case.
        // The current solution is to measure the time and try to figure out whether we've ticked onto
        // a new hour, which would mean that it's time to redo the visibility calculation. If new events
        // showed up for whatever other reason, then that would have nuked visibleEventList, so we'll
        // recompute that here as well.

        //        Log.v(TAG, "starting event pool: " + eventList.size());

        val gmtOffset = TimeWrapper.gmtOffset

        val localClipTime = TimeWrapper.localFloorHour
        val clipStartMillis = localClipTime - gmtOffset // convert from localtime back to GMT time for looking at events
        val clipEndMillis = clipStartMillis + 43200000  // 12 hours later

        val oldVisibleEventList = visibleEventList
        if (oldVisibleEventList != null)
            EventLayout.sanityTest(oldVisibleEventList, this.maxLevel, "Before clipping")

        // this used to compare to the GMT version (clipStartMillis), but this caused incorrect behavior
        // when the watch suddenly updated itself for a new timezone. Comparing to the *local* time
        // is the right answer.
        if (lastClipTime == localClipTime && oldVisibleEventList != null)
            return  // we've already done it, and we've got a cache of the results

        //        Log.v(TAG, "clipStart: " + TimeWrapper.formatGMTTime(clipStartMillis) + " (" + clipStartMillis +
        //                "), clipEnd: " + TimeWrapper.formatGMTTime(clipEndMillis) + " (" + clipEndMillis + ")");

        lastClipTime = localClipTime

        var tmpVisibleEventList = ArrayList<EventWrapper>()
        val oldEventList = eventList

        if(oldEventList != null) {
            Log.v(TAG, "clipping " + oldEventList.size + " raw events to fit the screen")

            // TODO: redo all of this using standard functional programming rather than mutable lists

            for (eventWrapper in oldEventList) {
                val e = eventWrapper.wireEvent
                var startTime = e.startTime
                var endTime = e.endTime

                //                Log.v(TAG, "New event: startTime: " + TimeWrapper.formatGMTTime(startTime) +
                //                        ", endTime: " + TimeWrapper.formatGMTTime(endTime));

                // clip the event to the screen
                if (startTime < clipStartMillis) startTime = clipStartMillis
                if (endTime > clipEndMillis) endTime = clipEndMillis


                //                Log.v(TAG, "-- Clipped: startTime: " + TimeWrapper.formatGMTTime(startTime) +
                //                        ", endTime: " + TimeWrapper.formatGMTTime(endTime));


                if (endTime < clipStartMillis || startTime > clipEndMillis)
                    continue // this one is off-screen

                if (startTime == clipStartMillis && endTime == clipEndMillis)
                    continue // this one covers the full 12-hour face of the watch; ignore for now

                // TODO if we ever do time-duration weighting of event thickness, then we can consider
                // bringing these back, as well as doing something more useful with all-day events,
                // which are similarly also ignored.

                tmpVisibleEventList.add(EventWrapper(WireEvent(startTime + gmtOffset, endTime + gmtOffset, e.displayColor)))
            }
        }

        // now, we run off and do screen layout
        var tmpMaxLevel: Int

        if (tmpVisibleEventList.size > 0) {
            // first, try the fancy constraint solver
            if (EventLayoutUniform.go(tmpVisibleEventList)) {
                // yeah, we succeeded
                tmpMaxLevel = EventLayoutUniform.MAXLEVEL
            } else {
                // something blew up with the Simplex solver, fall back to the cheesy, greedy algorithm
                Log.v(TAG, "falling back to older greedy method")
                tmpMaxLevel = EventLayout.go(tmpVisibleEventList)
            }

            EventLayout.sanityTest(tmpVisibleEventList, tmpMaxLevel, "After new event layout")
            Log.v(TAG, "maxLevel for new events: " + tmpMaxLevel)
            Log.v(TAG, "number of new events: " + tmpVisibleEventList.size)
            this.visibleEventList = tmpVisibleEventList
            this.maxLevel = tmpMaxLevel
        } else {
            this.visibleEventList = null
            this.maxLevel = 0;
            Log.v(TAG, "no events visible!")
        }
    }

    /**
     * This returns a list of *visible* events on the watchface, cropped to size, and adjusted to
     * the *local* timezone. If you want GMT events, which will not have been clipped, then use
     * getWireEventList().
     */
    fun getVisibleEventList(): List<EventWrapper>? {
        // should be fast, since mostly it will detect that nothing has changed
        computeVisibleEvents()
        return visibleEventList
    }

    var protobuf: ByteArray
        /**
         * marshalls into a protobuf for transmission elsewhere
         * @return the protobuf
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
                Log.v(TAG, "--> displayColor(" + Integer.toHexString(e.wireEvent.displayColor) + "), minLevel(" + e.minLevel + "), maxLevel(" + e.maxLevel + "), startTime(" + e.wireEvent.startTime + "), endTime(" + e.wireEvent.endTime + ")")
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
